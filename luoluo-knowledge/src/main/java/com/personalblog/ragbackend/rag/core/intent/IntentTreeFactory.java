package com.personalblog.ragbackend.rag.core.intent;

import com.personalblog.ragbackend.rag.enums.IntentKind;
import com.personalblog.ragbackend.rag.enums.IntentLevel;

import java.util.ArrayList;
import java.util.List;

public class IntentTreeFactory {
    private static final String KB_ID_GROUP = "1997855927072321537";
    private static final String KB_ID_BIZ = "1997857139737882625";

    public static List<RagIntentNode> buildIntentTree() {
        List<RagIntentNode> roots = new ArrayList<>();

        RagIntentNode group = node("group", "集团信息化", IntentLevel.DOMAIN, null, KB_ID_GROUP, IntentKind.KB);
        RagIntentNode hr = node("group-hr", "人事", IntentLevel.CATEGORY, group.getId(), KB_ID_GROUP, IntentKind.KB);
        hr.description = "招聘、入职、转正、离职、绩效、薪资、考勤、请假等人力资源相关问题";
        hr.setExamples(List.of("请假流程是怎样的？", "试用期多久转正？", "迟到会有什么处罚？"));

        RagIntentNode it = node("group-it", "IT支持", IntentLevel.CATEGORY, group.getId(), KB_ID_GROUP, IntentKind.KB);
        it.description = "VPN、邮箱、打印机、网络、电脑账号密码、办公软件等 IT 支持相关问题";
        it.setExamples(List.of("电脑打印机怎么连？", "公司 VPN 连不上怎么办？", "邮箱密码忘了怎么重置？"));

        RagIntentNode finance = node("group-finance", "财务", IntentLevel.CATEGORY, group.getId(), KB_ID_GROUP, IntentKind.KB);
        finance.description = "报销、付款、成本中心、预算等财务相关问题";
        finance.setExamples(List.of("差旅报销需要哪些材料？"));

        RagIntentNode financeInvoice = node("group-finance-invoice", "发票相关", IntentLevel.TOPIC, finance.getId(), KB_ID_GROUP, IntentKind.KB);
        financeInvoice.description = "获取公司发票抬头相关信息";
        financeInvoice.setExamples(List.of("发票抬头有哪些？"));
        financeInvoice.promptTemplate = FINANCE_INVOICE_PROMPT_TEMPLATE;
        finance.setChildren(List.of(financeInvoice));

        group.setChildren(List.of(hr, it, finance));
        roots.add(group);

        RagIntentNode biz = node("biz", "业务系统", IntentLevel.DOMAIN, null, KB_ID_BIZ, IntentKind.KB);
        RagIntentNode oa = node("biz-oa", "OA系统", IntentLevel.CATEGORY, biz.getId(), KB_ID_BIZ, IntentKind.KB);
        oa.description = "OA 系统相关，例如流程审批、待办、公告、文档中心等";
        oa.setExamples(List.of("OA系统主要提供哪些功能？", "请假审批在哪个菜单？"));

        RagIntentNode oaIntro = node("biz-oa-intro", "系统介绍", IntentLevel.TOPIC, oa.getId(), KB_ID_BIZ, IntentKind.KB);
        oaIntro.description = "OA 系统整体功能说明、主要模块、典型使用场景";
        oaIntro.setExamples(List.of("OA系统是做什么的？"));

        RagIntentNode oaSecurity = node("biz-oa-security", "数据安全", IntentLevel.TOPIC, oa.getId(), KB_ID_BIZ, IntentKind.KB);
        oaSecurity.description = "OA 系统的数据权限、访问控制、安全审计等相关说明";
        oaSecurity.setExamples(List.of("OA系统如何控制不同角色的权限？"));
        oa.setChildren(List.of(oaIntro, oaSecurity));

        RagIntentNode ins = node("biz-ins", "保险系统", IntentLevel.CATEGORY, biz.getId(), KB_ID_BIZ, IntentKind.KB);
        ins.description = "保险相关业务系统，例如投保、核保、理赔等的功能与架构说明";
        ins.setExamples(List.of("保险系统整体架构是怎样的？"));

        RagIntentNode insIntro = node("biz-ins-intro", "系统介绍", IntentLevel.TOPIC, ins.getId(), KB_ID_BIZ, IntentKind.KB);
        insIntro.description = "保险系统业务模块说明与主要流程介绍";
        insIntro.setExamples(List.of("保险系统都包含哪些子系统？"));

        RagIntentNode insArch = node("biz-ins-arch", "架构设计", IntentLevel.TOPIC, ins.getId(), KB_ID_BIZ, IntentKind.KB);
        insArch.description = "保险系统的技术架构、服务拆分、数据库设计等";
        insArch.setExamples(List.of("保险系统是如何做服务拆分的？"));

        RagIntentNode insSecurity = node("biz-ins-security", "数据安全", IntentLevel.TOPIC, ins.getId(), KB_ID_BIZ, IntentKind.KB);
        insSecurity.description = "保险系统的数据脱敏、权限控制、审计与合规等";
        insSecurity.setExamples(List.of("保险系统的敏感信息如何保护？"));
        ins.setChildren(List.of(insIntro, insArch, insSecurity));

        biz.setChildren(List.of(oa, ins));
        roots.add(biz);

        RagIntentNode sales = node("sales", "销售汇总数据统计", IntentLevel.DOMAIN, null, null, IntentKind.MCP);
        RagIntentNode dingTaskSales = node("sales-data", "销售数据统计", IntentLevel.CATEGORY, sales.getId(), null, IntentKind.MCP);
        dingTaskSales.mcpToolId = "sales_query";
        dingTaskSales.description = "销售数据统计，例如销售总额、销量、销售占比、销售趋势、销售预测等";
        dingTaskSales.setExamples(List.of("销售总额是多少？", "销量是多少？"));
        dingTaskSales.promptTemplate = MCP_SALES_DATA_PROMPT_TEMPLATE;
        dingTaskSales.paramPromptTemplate = MCP_SALES_DATA_PARAMETER_EXTRACT_PROMPT;
        sales.setChildren(List.of(dingTaskSales));
        roots.add(sales);

        RagIntentNode sys = node("sys", "系统交互", IntentLevel.DOMAIN, null, null, IntentKind.SYSTEM);
        RagIntentNode welcome = node("sys-welcome", "欢迎与问候", IntentLevel.CATEGORY, sys.getId(), null, IntentKind.SYSTEM);
        welcome.description = "用户与助手打招呼，例如：你好、早上好、hi、在吗等";
        welcome.setExamples(List.of("你好", "hello", "早上好", "在吗", "嗨"));

        RagIntentNode aboutBot = node("sys-about-bot", "关于助手", IntentLevel.CATEGORY, sys.getId(), null, IntentKind.SYSTEM);
        aboutBot.description = "询问助手是做什么的、是谁、能做什么等";
        aboutBot.setExamples(List.of("你是谁？", "你是做什么的", "你能帮我做什么？", "你是什么AI"));
        sys.setChildren(List.of(welcome, aboutBot));
        roots.add(sys);

        fillFullPath(roots, null);
        return roots;
    }

    private static RagIntentNode node(String id, String name, IntentLevel level, String parentId, String kbId, IntentKind kind) {
        RagIntentNode node = new RagIntentNode();
        node.setId(id);
        node.name = name;
        node.setLevel(level);
        node.setParentId(parentId);
        node.kbId = kbId;
        node.setKind(kind);
        node.children = new ArrayList<>();
        return node;
    }

    private static void fillFullPath(List<RagIntentNode> nodes, RagIntentNode parent) {
        for (RagIntentNode node : nodes) {
            node.fullPath = parent == null ? node.name : parent.fullPath + " > " + node.name;
            if (node.children != null && !node.children.isEmpty()) {
                fillFullPath(node.children, node);
            }
        }
    }

    private static final String FINANCE_INVOICE_PROMPT_TEMPLATE = """
            你是专业的企业发票信息查询助手，现在根据《文档内容》回答用户关于开票信息的问题，并抽取、整理标准化的发票信息。

            请严格遵守以下规则：
            1. 文档中的发票相关字段名不一定与标准字段完全一致，请根据语义进行统一映射。
            2. 当文档中字段名与标准字段语义相近时，请将其内容归一到对应的标准字段中；不要新增其他字段名。
            3. 回答必须严格基于《文档内容》，不得虚构任何信息。
            4. 查到至少一条发票信息时，必须先输出一段引导语，再输出具体内容。
            5. 如查询结果有多条，请输出“发票信息列表”。
            6. 每条发票信息请按统一格式输出。

            《文档内容》
            %s

            《用户问题》
            %s
            """;

    private static final String MCP_SALES_DATA_PARAMETER_EXTRACT_PROMPT = """
            Hello，你是一个高度专业且严谨的【工具参数提取器】。

            你的唯一任务是：严格按照提供的【工具定义】和【参数列表】的约束，从【用户问题】中提取所有必要的参数，并以 JSON 格式输出。

            <tool_definition>
            %s
            </tool_definition>

            <user_query>
            %s
            </user_query>
            """;

    private static final String MCP_SALES_DATA_PROMPT_TEMPLATE = """
            Hello，你是专业的企业智能数据助手。系统已调用内部工具获得了最新的【动态数据】。
            你的任务是将这些结构化数据转化为**业务化、易读的自然语言**回答。

            {{INTENT_RULES}}

            【动态数据】
            %s

            【用户问题】
            %s
            """;
}
