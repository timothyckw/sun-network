package org.tron.service.check;

import lombok.Getter;
import lombok.Setter;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Sha256Hash;
import org.tron.protos.Protocol.Transaction;
import org.tron.service.task.TaskEnum;

public class TransactionExtension {

  @Getter
  @Setter
  private TaskEnum type;
  @Getter
  @Setter
  private String transactionId;
  @Getter
  @Setter
  private Transaction transaction;

  public TransactionExtension(TaskEnum type, Transaction transaction) {
    this.type = type;
    this.transactionId = ByteArray
        .toHexString(Sha256Hash.hash(transaction.getRawData().toByteArray()));
    this.transaction = transaction;
  }

  public TransactionExtension(Transaction transaction) {
    this.transactionId = ByteArray
        .toHexString(Sha256Hash.hash(transaction.getRawData().toByteArray()));
    this.transaction = transaction;
  }

  public TransactionExtension(byte[] bytes) {
    // Transaction.parseFrom(bytes);
    this.transactionId = ByteArray
        .toHexString(Sha256Hash.hash(transaction.getRawData().toByteArray()));
    this.transaction = transaction;
  }
}