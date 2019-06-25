package org.tron.service.check;

import com.google.protobuf.InvalidProtocolBufferException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.tron.client.MainChainGatewayApi;
import org.tron.client.SideChainGatewayApi;
import org.tron.common.exception.RpcConnectException;
import org.tron.common.exception.TxExpiredException;
import org.tron.common.exception.TxFailException;
import org.tron.common.exception.TxRollbackException;
import org.tron.common.exception.TxValidateException;
import org.tron.common.utils.AlertUtil;
import org.tron.db.EventStore;
import org.tron.db.NonceStore;
import org.tron.db.TransactionExtensionStore;
import org.tron.protos.Sidechain.NonceStatus;
import org.tron.service.eventactuator.Actuator;
import org.tron.service.eventactuator.ActuatorRun;
import org.tron.service.task.InitTask;

@Slf4j
public class CheckTransaction {

  private static CheckTransaction instance = new CheckTransaction();

  public static CheckTransaction getInstance() {
    return instance;
  }

  private CheckTransaction() {
  }

  private final ScheduledExecutorService syncExecutor = Executors.newScheduledThreadPool(100);

  public void submitCheck(TransactionExtensionCapsule txExtensionCapsule, int submitCnt) {
    syncExecutor.schedule(() -> instance.checkTransaction(txExtensionCapsule, submitCnt), 60,
        TimeUnit.SECONDS);
  }

  private void checkTransaction(TransactionExtensionCapsule txExtensionCapsule, int checkCnt) {

    if (StringUtils.isEmpty(txExtensionCapsule.getTransactionId())) {
      return;
    }
    Boolean ret = checkTxInfoReturnNull(txExtensionCapsule);
    if (Objects.nonNull(ret)) {
      if (ret) {
        // success
        // TODO  transaction success put store
        byte[] nonceKeyBytes = txExtensionCapsule.getNonceKeyBytes();
        NonceStore.getInstance()
            .putData(nonceKeyBytes,
                ByteBuffer.allocate(4).putInt(NonceStatus.SUCCESS_VALUE).array());
        EventStore.getInstance().deleteData(nonceKeyBytes);
        TransactionExtensionStore.getInstance().deleteData(nonceKeyBytes);
        return;

      } else {
//        byte[] nonceKeyBytes = txExtensionCapsule.getNonceKeyBytes();
//        NonceStore.getInstance()
//            .putData(nonceKeyBytes,
//                ByteBuffer.allocate(4).putInt(NonceStatus.).array());
//        EventStore.getInstance().deleteData(nonceKeyBytes);
//        TransactionExtensionStore.getInstance().deleteData(nonceKeyBytes);
        String msg = String.format("tx: %s, fail, please resolve this problem by reviewing and "
            + "inspecting logs of oracle", txExtensionCapsule.getTransactionId());
        logger.error(msg);
        AlertUtil.sendAlert(msg);
        return;
      }
    }
    // ret == null
    if (checkCnt > 5) {
      String msg = String.format("tx: %s, not found, check count: %d, exceeds 5 times",
          txExtensionCapsule.getTransactionId(), checkCnt);
      AlertUtil.sendAlert(msg);
      logger.error(msg);
      return;
    }
    try {
      broadcastTransaction(txExtensionCapsule);
      byte[] nonceKeyBytes = txExtensionCapsule.getNonceKeyBytes();
      byte[] data = EventStore.getInstance().getData(nonceKeyBytes);
      try {
        Actuator eventActuator = InitTask.getActuatorByEventMsg(data);
        ActuatorRun.getInstance().start(eventActuator);
      } catch (InvalidProtocolBufferException e2) {
        logger.error("", e2);
      }

    } catch (RpcConnectException e1) {
      AlertUtil.sendAlert(
          String.format("tx: %s, rpc connect fail", txExtensionCapsule.getTransactionId()));
      logger.error(e1.getMessage(), e1);
      return;
    } catch (TxValidateException e1) {
      AlertUtil.sendAlert(String.format("tx: %s, validation fail, will not exist on chain",
          txExtensionCapsule.getTransactionId()));
      logger.error(e1.getMessage(), e1);
      return;
    } catch (TxExpiredException e) {
      e.printStackTrace();
    }
  }

  private Boolean checkTxInfoReturnNull(TransactionExtensionCapsule txExtensionCapsule) {
    try {
      switch (txExtensionCapsule.getType()) {
        case MAIN_CHAIN:
          MainChainGatewayApi.checkTxInfo(txExtensionCapsule.getTransactionId());
          break;
        case SIDE_CHAIN:
          SideChainGatewayApi.checkTxInfo(txExtensionCapsule.getTransactionId());
          break;
      }
    } catch (TxRollbackException e) {
      return null;
    } catch (TxFailException e) {
      return false;
    }
    return true;

  }

  public boolean broadcastTransaction(TransactionExtensionCapsule txExtensionCapsule)
      throws RpcConnectException, TxValidateException, TxExpiredException {
    switch (txExtensionCapsule.getType()) {
      case MAIN_CHAIN:
        return MainChainGatewayApi.broadcast(txExtensionCapsule.getTransaction());
      case SIDE_CHAIN:
        return SideChainGatewayApi.broadcast(txExtensionCapsule.getTransaction());
      default:
        return false;
    }
  }
}
