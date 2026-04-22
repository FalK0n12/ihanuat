package com.ihanuat.mod;

public class I18n {
    public static boolean isZh() {
        return MacroConfig.language == MacroConfig.Language.SIMPLIFIED_CHINESE;
    }

    public static String tr(String english, String simplifiedChinese) {
        return isZh() ? simplifiedChinese : english;
    }
}
