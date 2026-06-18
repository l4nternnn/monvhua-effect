package com.kuilunfuzhe.monvhua.role;

import java.util.Map;

public final class RoleRegistry {
    private static final Map<String, RoleInfo> TAG_TO_ROLE = Map.ofEntries(
            Map.entry("ema", new RoleInfo("樱羽艾玛", 0xfc8eac)),
            Map.entry("cero", new RoleInfo("二阶堂希罗", 0x8b0000)),
            Map.entry("nnk", new RoleInfo("黑部奈叶香", 0x555555)),
            Map.entry("mago", new RoleInfo("宝生玛格", 0xAA00AA)),
            Map.entry("leiya", new RoleInfo("莲见蕾雅", 0xFFAA00)),
            Map.entry("milya", new RoleInfo("佐伯米利亚", 0xFFFF55)),
            Map.entry("sherry", new RoleInfo("橘雪莉", 0x1e90ff)),
            Map.entry("yalisa", new RoleInfo("紫藤亚里沙", 0xB1B7AC)),
            Map.entry("noa", new RoleInfo("城崎诺亚", 0x55FFFF)),
            Map.entry("anan", new RoleInfo("夏目安安", 0x240090)),
            Map.entry("yuki", new RoleInfo("月代雪", 0xe0ffff)),
            Map.entry("mll", new RoleInfo("冰上梅露露", 0xddb6ff)),
            Map.entry("coco", new RoleInfo("泽渡可可", 0xff6700)),
            Map.entry("hanna", new RoleInfo("远野汉娜", 0x5f9e3f))
    );

    private RoleRegistry() {
    }

    public static RoleInfo getRoleInfoByTag(String tag) {
        return TAG_TO_ROLE.get(tag);
    }
}
