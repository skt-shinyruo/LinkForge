package com.linkforge.foundation.runtime.startup;

import java.util.List;

public interface StartupCheck {

    void validate(boolean strict, List<String> errors);
}
