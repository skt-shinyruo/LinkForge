package com.linkforge.foundation.runtime.tx;

import com.linkforge.foundation.tx.AfterCommit;
import com.linkforge.foundation.tx.PostCommitHookPort;
import org.springframework.stereotype.Component;

@Component
public final class SpringPostCommitHookAdapter implements PostCommitHookPort {

    @Override
    public void run(Runnable action) {
        AfterCommit.run(action);
    }
}
