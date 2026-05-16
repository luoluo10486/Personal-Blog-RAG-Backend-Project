package com.personalblog.ragbackend.rag.core.intent;

import com.personalblog.ragbackend.rag.enums.IntentKind;
import com.personalblog.ragbackend.rag.enums.IntentLevel;

import java.util.ArrayList;
import java.util.List;

import static com.personalblog.ragbackend.rag.enums.IntentLevel.CATEGORY;
import static com.personalblog.ragbackend.rag.enums.IntentLevel.DOMAIN;
import static com.personalblog.ragbackend.rag.enums.IntentLevel.TOPIC;

public class IntentTreeFactory {
    private static final String KB_ID_GROUP = "1997855927072321537";
    private static final String KB_ID_BIZ = "1997857139737882625";

    public static List<IntentNode> buildIntentTree() {
        List<IntentNode> roots = new ArrayList<>();

        IntentNode group = IntentNode.builder()
                .id("group")
                .kbId(KB_ID_GROUP)
                .name("闆嗗洟淇℃伅鍖?")
                .level(DOMAIN)
                .kind(IntentKind.KB)
                .build();

        IntentNode hr = IntentNode.builder()
                .id("group-hr")
                .kbId(KB_ID_GROUP)
                .name("浜轰簨")
                .level(CATEGORY)
                .parentId(group.getId())
                .kind(IntentKind.KB)
                .description("鎷涜仒銆佸叆鑱屻€佽浆姝ｃ€佺鑱屻€佺哗鏁堛€佽柂璧勩€佽€冨嫟銆佽鍋囩瓑浜哄姏璧勬簮鐩稿叧闂")
                .examples(List.of(
                        "璇峰亣娴佺▼鏄€庢牱鐨勶紵",
                        "璇曠敤鏈熷涔呰浆姝ｏ紵",
                        "杩熷埌浼氭湁浠€涔堝缃氾紵"
                ))
                .build();

        IntentNode it = IntentNode.builder()
                .id("group-it")
                .kbId(KB_ID_GROUP)
                .name("IT鏀寔")
                .level(CATEGORY)
                .parentId(group.getId())
                .kind(IntentKind.KB)
                .description("VPN銆侀偖绠便€佹墦鍗版満銆佺綉缁溿€佺數鑴戣处鍙峰瘑鐮併€佸姙鍏蒋浠剁瓑 IT 鏀寔鐩稿叧闂")
                .examples(List.of(
                        "鐢佃剳鎵撳嵃鏈烘€庝箞杩烇紵",
                        "鍏徃 VPN 杩炰笉涓婃€庝箞鍔烇紵",
                        "閭瀵嗙爜蹇樹簡鎬庝箞閲嶇疆锛?"
                ))
                .build();

        IntentNode finance = IntentNode.builder()
                .id("group-finance")
                .kbId(KB_ID_GROUP)
                .name("璐㈠姟")
                .level(CATEGORY)
                .parentId(group.getId())
                .kind(IntentKind.KB)
                .description("鎶ラ攢銆佷粯娆俱€佹垚鏈腑蹇冦€侀绠楃瓑璐㈠姟鐩稿叧闂")
                .examples(List.of("宸梾鎶ラ攢闇€瑕佸摢浜涙潗鏂欙紵"))
                .build();

        IntentNode financeInvoice = IntentNode.builder()
                .id("group-finance-invoice")
                .kbId(KB_ID_GROUP)
                .name("鍙戠エ鐩稿叧")
                .level(TOPIC)
                .parentId(finance.getId())
                .kind(IntentKind.KB)
                .description("鑾峰彇鍏徃鍙戠エ鎶ご鐩稿叧淇℃伅")
                .examples(List.of("鍙戠エ鎶ご鏈夊摢浜涳紵"))
                .promptTemplate(FINANCE_INVOICE_PROMPT_TEMPLATE)
                .build();
        finance.setChildren(List.of(financeInvoice));

        group.setChildren(List.of(hr, it, finance));
        roots.add(group);

        IntentNode biz = IntentNode.builder()
                .id("biz")
                .kbId(KB_ID_BIZ)
                .name("涓氬姟绯荤粺")
                .level(DOMAIN)
                .kind(IntentKind.KB)
                .build();

        IntentNode oa = IntentNode.builder()
                .id("biz-oa")
                .kbId(KB_ID_BIZ)
                .name("OA绯荤粺")
                .level(CATEGORY)
                .parentId(biz.getId())
                .kind(IntentKind.KB)
                .description("OA 绯荤粺鐩稿叧锛屼緥濡傛祦绋嬪鎵广€佸緟鍔炪€佸叕鍛娿€佹枃妗ｄ腑蹇冪瓑")
                .examples(List.of(
                        "OA绯荤粺涓昏鎻愪緵鍝簺鍔熻兘锛?",
                        "璇峰亣瀹℃壒鍦ㄥ摢涓彍鍗曪紵"
                ))
                .build();

        IntentNode oaIntro = IntentNode.builder()
                .id("biz-oa-intro")
                .kbId(KB_ID_BIZ)
                .name("绯荤粺浠嬬粛")
                .level(TOPIC)
                .parentId(oa.getId())
                .kind(IntentKind.KB)
                .description("OA 绯荤粺鏁翠綋鍔熻兘璇存槑銆佷富瑕佹ā鍧椼€佸吀鍨嬩娇鐢ㄥ満鏅?")
                .examples(List.of("OA绯荤粺鏄仛浠€涔堢殑锛?"))
                .build();

        IntentNode oaSecurity = IntentNode.builder()
                .id("biz-oa-security")
                .kbId(KB_ID_BIZ)
                .name("鏁版嵁瀹夊叏")
                .level(TOPIC)
                .parentId(oa.getId())
                .kind(IntentKind.KB)
                .description("OA 绯荤粺鐨勬暟鎹潈闄愩€佽闂帶鍒躲€佸畨鍏ㄥ璁＄瓑鐩稿叧璇存槑")
                .examples(List.of("OA绯荤粺濡備綍鎺у埗涓嶅悓瑙掕壊鐨勬潈闄愶紵"))
                .build();
        oa.setChildren(List.of(oaIntro, oaSecurity));

        IntentNode ins = IntentNode.builder()
                .id("biz-ins")
                .kbId(KB_ID_BIZ)
                .name("淇濋櫓绯荤粺")
                .level(CATEGORY)
                .parentId(biz.getId())
                .kind(IntentKind.KB)
                .description("淇濋櫓鐩稿叧涓氬姟绯荤粺锛屽鎶曚繚銆佹牳淇濄€佺悊璧旂瓑鐨勫姛鑳戒笌鏋舵瀯璇存槑")
                .examples(List.of("淇濋櫓绯荤粺鏁翠綋鏋舵瀯鏄€庢牱鐨勶紵"))
                .build();

        IntentNode insIntro = IntentNode.builder()
                .id("biz-ins-intro")
                .kbId(KB_ID_BIZ)
                .name("绯荤粺浠嬬粛")
                .level(TOPIC)
                .parentId(ins.getId())
                .kind(IntentKind.KB)
                .description("淇濋櫓绯荤粺涓氬姟妯″潡璇存槑涓庝富瑕佹祦绋嬩粙缁?")
                .examples(List.of("淇濋櫓绯荤粺閮藉寘鍚摢浜涘瓙绯荤粺锛?"))
                .build();

        IntentNode insArch = IntentNode.builder()
                .id("biz-ins-arch")
                .kbId(KB_ID_BIZ)
                .name("鏋舵瀯璁捐")
                .level(TOPIC)
                .parentId(ins.getId())
                .kind(IntentKind.KB)
                .description("淇濋櫓绯荤粺鐨勬妧鏈灦鏋勩€佹湇鍔℃媶鍒嗐€佹暟鎹簱璁捐绛?")
                .examples(List.of("淇濋櫓绯荤粺鏄浣曞仛鏈嶅姟鎷嗗垎鐨勶紵"))
                .build();

        IntentNode insSecurity = IntentNode.builder()
                .id("biz-ins-security")
                .kbId(KB_ID_BIZ)
                .name("鏁版嵁瀹夊叏")
                .level(TOPIC)
                .parentId(ins.getId())
                .kind(IntentKind.KB)
                .description("淇濋櫓绯荤粺鐨勬暟鎹劚鏁忋€佹潈闄愭帶鍒躲€佸璁′笌鍚堣绛?")
                .examples(List.of("淇濋櫓绯荤粺鐨勬晱鎰熶俊鎭浣曚繚鎶わ紵"))
                .build();
        ins.setChildren(List.of(insIntro, insArch, insSecurity));

        biz.setChildren(List.of(oa, ins));
        roots.add(biz);

        IntentNode sales = IntentNode.builder()
                .id("sales")
                .name("閿€鍞眹鎬绘暟鎹粺璁?")
                .level(DOMAIN)
                .kind(IntentKind.MCP)
                .build();

        IntentNode salesData = IntentNode.builder()
                .id("sales-data")
                .name("閿€鍞暟鎹粺璁?")
                .level(CATEGORY)
                .parentId(sales.getId())
                .mcpToolId("sales_query")
                .kind(IntentKind.MCP)
                .promptTemplate(MCP_SALES_DATA_PROMPT_TEMPLATE)
                .paramPromptTemplate(MCP_SALES_DATA_PARAMETER_EXTRACT_PROMPT)
                .description("閿€鍞暟鎹粺璁★紝濡傦細閿€鍞€婚銆侀攢鍞噺銆侀攢鍞崰姣斻€侀攢鍞秼鍔裤€侀攢鍞娴嬬瓑")
                .examples(List.of("閿€鍞€婚鏄灏戯紵", "閿€鍞噺鏄灏戯紵"))
                .build();
        sales.setChildren(List.of(salesData));
        roots.add(sales);

        IntentNode sys = IntentNode.builder()
                .id("sys")
                .name("绯荤粺浜や簰")
                .level(DOMAIN)
                .kind(IntentKind.SYSTEM)
                .build();

        IntentNode welcome = IntentNode.builder()
                .id("sys-welcome")
                .name("娆㈣繋涓庨棶鍊?")
                .level(CATEGORY)
                .parentId(sys.getId())
                .description("鐢ㄦ埛涓庡姪鎵嬫墦鎷涘懠锛屽锛氫綘濂姐€佹棭涓婂ソ銆乭i銆佸湪鍚?绛?")
                .examples(List.of("浣犲ソ", "hello", "鏃╀笂濂?", "鍦ㄥ悧", "鍡?"))
                .kind(IntentKind.SYSTEM)
                .build();

        IntentNode aboutBot = IntentNode.builder()
                .id("sys-about-bot")
                .name("鍏充簬鍔╂墜")
                .level(CATEGORY)
                .parentId(sys.getId())
                .description("璇㈤棶鍔╂墜鏄仛浠€涔堢殑銆佹槸璋併€佽兘鍋氫粈涔堢瓑")
                .examples(List.of("浣犳槸璋?", "浣犳槸鍋氫粈涔堢殑", "浣犺兘甯垜鍋氫粈涔?", "浣犳槸浠€涔圓I"))
                .kind(IntentKind.SYSTEM)
                .build();

        sys.setChildren(List.of(welcome, aboutBot));
        roots.add(sys);

        fillFullPath(roots, null);
        return roots;
    }

    private static void fillFullPath(List<IntentNode> nodes, IntentNode parent) {
        for (IntentNode node : nodes) {
            node.setFullPath(parent == null ? node.getName() : parent.getFullPath() + " > " + node.getName());
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                fillFullPath(node.getChildren(), node);
            }
        }
    }

    private static final String FINANCE_INVOICE_PROMPT_TEMPLATE = """
            浣犳槸涓撲笟鐨勪紒涓氬彂绁ㄤ俊鎭煡璇㈠姪鎵嬶紝鐜板湪鏍规嵁銆愭枃妗ｅ唴瀹广€嬪洖绛旂敤鎴峰叧浜庡紑绁ㄤ俊鎭殑闂锛屽苟鎶藉彇銆佹暣鐞嗘爣鍑嗗寲鐨勫彂绁ㄤ俊鎭€?
            璇蜂弗鏍奸伒瀹堜互涓嬭鍒欙細
            1. 鏂囨。涓殑鍙戠エ鐩稿叧瀛楁鍚嶄笉涓€瀹氫笌鏍囧噯瀛楁瀹屽叏涓€鑷达紝璇锋牴鎹涔夎繘琛岀粺涓€鏄犲皠銆?
            2. 褰撴枃妗ｄ腑瀛楁鍚嶄笌鏍囧噯瀛楁璇箟鐩歌繎鏃讹紝璇峰皢鍏跺唴瀹瑰綊涓€鍒板搴旂殑鏍囧噯瀛楁涓紱涓嶈鏂板鍏朵粬瀛楁鍚嶃€?
            3. 鍥炵瓟蹇呴』涓ユ牸鍩轰簬銆愭枃妗ｅ唴瀹广€嬶紝涓嶅緱铏氭瀯浠讳綍淇℃伅銆?
            4. 鏌ュ埌鑷冲皯涓€鏉″彂绁ㄤ俊鎭椂锛屽繀椤诲厛杈撳嚭涓€娈靛紩瀵艰锛屽啀杈撳嚭鍏蜂綋鍐呭銆?
            5. 濡傛煡璇㈢粨鏋滄湁澶氭潯锛岃杈撳嚭鈥滃彂绁ㄤ俊鎭垪琛ㄢ€濄€?
            6. 姣忔潯鍙戠エ淇℃伅璇锋寜缁熶竴鏍煎紡杈撳嚭銆?
            銆愭枃妗ｅ唴瀹广€?
            %s

            銆愮敤鎴烽棶棰樸€?
            %s
            """;

    public static final String MCP_SALES_DATA_PARAMETER_EXTRACT_PROMPT = """
            Hello锛屼綘鏄竴涓珮搴︿笓涓氫笖涓ヨ皑鐨勩€愬伐鍏峰弬鏁版彁鍙栧櫒銆戙€?
            浣犵殑鍞竴浠诲姟鏄細涓ユ牸鎸夌収鎻愪緵鐨勩€愬伐鍏峰畾涔夈€戝拰銆愬弬鏁板垪琛ㄣ€戠殑绾︽潫锛屼粠銆愮敤鎴烽棶棰樸€戜腑鎻愬彇鎵€鏈夊繀瑕佺殑鍙傛暟锛屽苟浠?JSON 鏍煎紡杈撳嚭銆?
            <tool_definition>
            %s
            </tool_definition>

            <user_query>
            %s
            </user_query>
            """;

    private static final String MCP_SALES_DATA_PROMPT_TEMPLATE = """
            Hello锛屼綘鏄笓涓氱殑浼佷笟鏅鸿兘鏁版嵁鍔╂墜銆傜郴缁熷凡璋冪敤鍐呴儴宸ュ叿鑾峰緱浜嗘渶鏂扮殑銆愬姩鎬佹暟鎹€戙€?
            浣犵殑浠诲姟鏄皢杩欎簺缁撴瀯鍖栨暟鎹浆鍖栦负**涓氬姟鍖栥€佹槗璇荤殑鑷劧璇█**鍥炵瓟銆?
            {{INTENT_RULES}}

            銆愬姩鎬佹暟鎹€?
            %s

            銆愮敤鎴烽棶棰樸€?
            %s
            """;
}
