package dev.iamkavindu.reconflow.transactions;

public interface TransactionCommands {

    TransactionView recordTransaction(RecordTransactionCommand command);
}
