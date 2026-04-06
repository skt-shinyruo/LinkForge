package com.linkforge.foundation.tx;

public interface PostCommitHookPort {

    void run(Runnable action);
}
