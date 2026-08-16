package com.kghua.npcai.data;

import java.util.*;

/**
 * 列车角色中文名 -> 英文代码映射表。
 * 数据来源：03-知识库与数据梳理/新残月角色详细资料.md
 */
public class RoleRepository {
    private static final Map<String, String> CHINESE_TO_ENGLISH = new LinkedHashMap<>();
    private static final Map<String, String> ENGLISH_TO_CHINESE = new HashMap<>();

    static {
        CHINESE_TO_ENGLISH.put("Dream", "dream");
        CHINESE_TO_ENGLISH.put("变形者", "morphling");
        CHINESE_TO_ENGLISH.put("布袋鬼", "ma_chen_xu");
        CHINESE_TO_ENGLISH.put("操纵师", "manipulator");
        CHINESE_TO_ENGLISH.put("仇杀客", "blood_feudist");
        CHINESE_TO_ENGLISH.put("迪奥", "dio");
        CHINESE_TO_ENGLISH.put("毒师", "poisoner");
        CHINESE_TO_ENGLISH.put("观者", "watcher");
        CHINESE_TO_ENGLISH.put("刽子手", "executioner");
        CHINESE_TO_ENGLISH.put("画家", "painter");
        CHINESE_TO_ENGLISH.put("怀旧者", "nostalgist");
        CHINESE_TO_ENGLISH.put("交换者", "swapper");
        CHINESE_TO_ENGLISH.put("静语者", "silencer");
        CHINESE_TO_ENGLISH.put("猎人", "hunter");
        CHINESE_TO_ENGLISH.put("迷失杀手", "lost_killer");
        CHINESE_TO_ENGLISH.put("模仿者", "imitator");
        CHINESE_TO_ENGLISH.put("派对狂", "party_killer");
        CHINESE_TO_ENGLISH.put("破法者", "spellbreaker");
        CHINESE_TO_ENGLISH.put("潜行者", "stalker");
        CHINESE_TO_ENGLISH.put("强盗", "bandit");
        CHINESE_TO_ENGLISH.put("窃皮者", "skincrawler");
        CHINESE_TO_ENGLISH.put("清道夫", "cleaner");
        CHINESE_TO_ENGLISH.put("忍者", "ninja");
        CHINESE_TO_ENGLISH.put("设陷者", "trapper");
        CHINESE_TO_ENGLISH.put("水鬼", "water_ghost");
        CHINESE_TO_ENGLISH.put("亡灵之主", "undead_lord");
        CHINESE_TO_ENGLISH.put("巫师", "wizard");
        CHINESE_TO_ENGLISH.put("小镇做题家", "exampler");
        CHINESE_TO_ENGLISH.put("阴谋家", "conspirator");
        CHINESE_TO_ENGLISH.put("影隼", "shadow_falcon");
        CHINESE_TO_ENGLISH.put("幽灵", "phantom");
        CHINESE_TO_ENGLISH.put("冤魂", "wraith_assassin");
        CHINESE_TO_ENGLISH.put("炸弹客", "bomber");
        CHINESE_TO_ENGLISH.put("滞时鬼", "delayer");
        CHINESE_TO_ENGLISH.put("咒术师", "warlock");
        CHINESE_TO_ENGLISH.put("殡仪员", "mortician");
        CHINESE_TO_ENGLISH.put("乘务员", "attendant");
        CHINESE_TO_ENGLISH.put("厨师", "chef");
        CHINESE_TO_ENGLISH.put("船长", "conductor");
        CHINESE_TO_ENGLISH.put("大嗓门", "noisemaker");
        CHINESE_TO_ENGLISH.put("大侦探", "great_detective");
        CHINESE_TO_ENGLISH.put("蛋糕师", "cake_maker");
        CHINESE_TO_ENGLISH.put("捣蛋鬼", "prankster");
        CHINESE_TO_ENGLISH.put("斗士", "fighter");
        CHINESE_TO_ENGLISH.put("飞行员", "pilot");
        CHINESE_TO_ENGLISH.put("复仇者", "avenger");
        CHINESE_TO_ENGLISH.put("歌手", "singer");
        CHINESE_TO_ENGLISH.put("工程师", "engineer");
        CHINESE_TO_ENGLISH.put("故障机器人", "glitch_robot");
        CHINESE_TO_ENGLISH.put("广播员", "broadcaster");
        CHINESE_TO_ENGLISH.put("悍匪", "gangsters");
        CHINESE_TO_ENGLISH.put("红海军", "better_vigilante");
        CHINESE_TO_ENGLISH.put("会计", "accountant");
        CHINESE_TO_ENGLISH.put("记者", "awesome_binglus");
        CHINESE_TO_ENGLISH.put("监察员", "monitor");
        CHINESE_TO_ENGLISH.put("建筑师", "builder");
        CHINESE_TO_ENGLISH.put("酒保", "bartender");
        CHINESE_TO_ENGLISH.put("老人", "oldman");
        CHINESE_TO_ENGLISH.put("冒险家", "adventurer");
        CHINESE_TO_ENGLISH.put("明星", "star");
        CHINESE_TO_ENGLISH.put("魔术师", "magician");
        CHINESE_TO_ENGLISH.put("皮革噶的", "leather_pig");
        CHINESE_TO_ENGLISH.put("钳工", "fitter");
        CHINESE_TO_ENGLISH.put("潜水员", "diver");
        CHINESE_TO_ENGLISH.put("肉汁", "meatball");
        CHINESE_TO_ENGLISH.put("摄影师", "photographer");
        CHINESE_TO_ENGLISH.put("搜救员", "rescuer");
        CHINESE_TO_ENGLISH.put("算命大师", "fortuneteller");
        CHINESE_TO_ENGLISH.put("锁匠", "locksmith");
        CHINESE_TO_ENGLISH.put("探员", "agent");
        CHINESE_TO_ENGLISH.put("退伍军人", "veteran");
        CHINESE_TO_ENGLISH.put("巫毒师", "voodoo");
        CHINESE_TO_ENGLISH.put("咸鱼", "salted_fish");
        CHINESE_TO_ENGLISH.put("消防员", "firefighter");
        CHINESE_TO_ENGLISH.put("小透明", "ghost");
        CHINESE_TO_ENGLISH.put("心理学家", "psychologist");
        CHINESE_TO_ENGLISH.put("信使", "courier");
        CHINESE_TO_ENGLISH.put("驯马师", "tamer");
        CHINESE_TO_ENGLISH.put("验尸官", "coroner");
        CHINESE_TO_ENGLISH.put("药剂师", "alchemist");
        CHINESE_TO_ENGLISH.put("医生", "doctor");
        CHINESE_TO_ENGLISH.put("愚者", "the_fool");
        CHINESE_TO_ENGLISH.put("玉将军", "jade_general");
        CHINESE_TO_ENGLISH.put("运动员", "athlete");
        CHINESE_TO_ENGLISH.put("占卜家", "diviner");
        CHINESE_TO_ENGLISH.put("召回者", "recaller");
        CHINESE_TO_ENGLISH.put("钟表匠", "clockmaker");
        CHINESE_TO_ENGLISH.put("保安", "guard");
        CHINESE_TO_ENGLISH.put("承太郎", "jojo");
        CHINESE_TO_ENGLISH.put("诡客", "guest_ghost");
        CHINESE_TO_ENGLISH.put("鬼眼·杨间", "ghost_eye");
        CHINESE_TO_ENGLISH.put("海王", "sea_king");
        CHINESE_TO_ENGLISH.put("警卫", "sheriff");
        CHINESE_TO_ENGLISH.put("里昂", "leon");
        CHINESE_TO_ENGLISH.put("特警", "swast");
        CHINESE_TO_ENGLISH.put("武术教官", "martial_arts_instructor");
        CHINESE_TO_ENGLISH.put("巡警", "patroller");
        CHINESE_TO_ENGLISH.put("游侠", "elf");
        CHINESE_TO_ENGLISH.put("爱慕者", "admirer");
        CHINESE_TO_ENGLISH.put("风精灵", "wind_yaose");
        CHINESE_TO_ENGLISH.put("雇佣兵", "mercenary");
        CHINESE_TO_ENGLISH.put("幻音师", "musician_phantom");
        CHINESE_TO_ENGLISH.put("傀儡师", "puppeteer");
        CHINESE_TO_ENGLISH.put("丘比特", "cupid");
        CHINESE_TO_ENGLISH.put("秃鹫", "vulture");
        CHINESE_TO_ENGLISH.put("嬉命人", "embalmer");
        CHINESE_TO_ENGLISH.put("小丑", "jester");
        CHINESE_TO_ENGLISH.put("疫使", "infected");
        CHINESE_TO_ENGLISH.put("葬仪", "mortician_bodymaker");
        CHINESE_TO_ENGLISH.put("指挥官", "commander");
        CHINESE_TO_ENGLISH.put("阿蒙", "amon");
        CHINESE_TO_ENGLISH.put("秉烛人", "candlebearer");
        CHINESE_TO_ENGLISH.put("布谷鸟", "cuckoo");
        CHINESE_TO_ENGLISH.put("赌徒", "gambler");
        CHINESE_TO_ENGLISH.put("渡鸦", "raven");
        CHINESE_TO_ENGLISH.put("黑白", "monokuma");
        CHINESE_TO_ENGLISH.put("红尘客", "wayfarer");
        CHINESE_TO_ENGLISH.put("记录员", "recorder");
        CHINESE_TO_ENGLISH.put("家族保护伞", "parasol");
        CHINESE_TO_ENGLISH.put("家族保姆", "nutritionist");
        CHINESE_TO_ENGLISH.put("家族教徒", "mafioso");
        CHINESE_TO_ENGLISH.put("家族侍卫", "janitor");
        CHINESE_TO_ENGLISH.put("教父", "godfather");
        CHINESE_TO_ENGLISH.put("年兽", "nianshou");
        CHINESE_TO_ENGLISH.put("宿命的罪人", "doomed_sinner");
        CHINESE_TO_ENGLISH.put("鹈鹕", "pelican");
        CHINESE_TO_ENGLISH.put("推理师", "reasoner");
        CHINESE_TO_ENGLISH.put("小偷", "thief");
        for (Map.Entry<String, String> e : CHINESE_TO_ENGLISH.entrySet()) {
            ENGLISH_TO_CHINESE.put(e.getValue().toLowerCase(Locale.ROOT), e.getKey());
        }
    }

    private RoleRepository() {}

    /** 根据中文名返回英文代码；找不到返回原字符串 */
    public static String getEnglishId(String chineseName) {
        return CHINESE_TO_ENGLISH.getOrDefault(chineseName, chineseName);
    }

    /** 根据输入内容返回联想列表 */
    public static List<String> getSuggestions(String input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        String key = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String cn : CHINESE_TO_ENGLISH.keySet()) {
            if (cn.toLowerCase(Locale.ROOT).contains(key)) {
                result.add(cn);
                if (result.size() >= 8) break;
            }
        }
        return result;
    }

    /** 返回所有中文角色名 */
    public static List<String> getAllChineseNames() {
        return new ArrayList<>(CHINESE_TO_ENGLISH.keySet());
    }

    /** 返回中文名对应的英文代码；若输入已是英文代码则返回自身 */
    public static String resolveRoleId(String input) {
        if (input == null || input.isEmpty()) return "";
        String mapped = CHINESE_TO_ENGLISH.get(input);
        if (mapped != null) return mapped;
        return ENGLISH_TO_CHINESE.getOrDefault(input.toLowerCase(Locale.ROOT), input);
    }
}