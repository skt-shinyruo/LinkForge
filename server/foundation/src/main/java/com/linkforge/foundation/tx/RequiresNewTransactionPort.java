package com.linkforge.foundation.tx;

public interface RequiresNewTransactionPort {

    void run(Runnable action);
}
