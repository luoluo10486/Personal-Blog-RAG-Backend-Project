package com.personalblog.ragbackend.mcp.resources;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EnterpriseResources {

    public static final String RETURN_POLICY_URI = "docs://return-policy";
    public static final String ORDER_DETAIL_TEMPLATE_URI = "order://{orderId}";
    public static final String ORDER_DETAIL_PREFIX = "order://";

    public ResourceDefinition returnPolicyResource() {
        return new ResourceDefinition(
                RETURN_POLICY_URI,
                "退货政策",
                "公司的退货政策文档，包含退货条件、时限、流程等信息",
                "text/plain"
        );
    }

    public String getReturnPolicy() {
        return """
                【退货政策】

                1. 退货时限：自收货之日起 7 天内可申请无理由退货。
                2. 退货条件：
                   - 商品未拆封、未使用、不影响二次销售
                   - 赠品需一并退回
                   - 定制商品、鲜活易腐商品不支持无理由退货
                3. 退货流程：
                   - 在“我的订单”中提交退货申请
                   - 等待客服审核（1~2 个工作日）
                   - 审核通过后按指定地址寄回商品
                   - 收到商品并验收后 3~5 个工作日内退款
                4. 退款方式：原路退回（支付宝/微信/银行卡）。
                5. 运费说明：因质量问题退货，运费由公司承担；无理由退货，运费由用户承担。
                """;
    }

    public ResourceDefinition orderDetailResource() {
        return new ResourceDefinition(
                ORDER_DETAIL_TEMPLATE_URI,
                "订单详情",
                "根据订单号查询订单详情。读取时请使用形如 order://ORD-12345 或 order://12345 的 URI。",
                "application/json"
        );
    }

    public String getOrderDetail(String resourceUri) {
        String orderId = extractOrderId(resourceUri);
        return """
                {
                  "orderId": "%s",
                  "productName": "iPhone 16 Pro 256GB 沙漠钛金属",
                  "price": 8999,
                  "quantity": 1,
                  "status": "已签收",
                  "orderTime": "2026-03-01 14:30:00",
                  "deliveryTime": "2026-03-05 10:15:00",
                  "address": "北京市朝阳区xxx小区"
                }
                """.formatted(orderId);
    }

    public boolean isReturnPolicy(String resourceUri) {
        return RETURN_POLICY_URI.equals(resourceUri);
    }

    public boolean isOrderDetail(String resourceUri) {
        return resourceUri != null && !resourceUri.isBlank() && resourceUri.startsWith(ORDER_DETAIL_PREFIX);
    }

    public List<ResourceDefinition> definitions() {
        return List.of(returnPolicyResource(), orderDetailResource());
    }

    private String extractOrderId(String resourceUri) {
        String orderId = resourceUri.substring(ORDER_DETAIL_PREFIX.length()).trim();
        if (orderId.isEmpty() || "{orderId}".equals(orderId)) {
            throw new IllegalArgumentException("orderId 不能为空，请使用形如 order://12345 的资源 URI");
        }
        return orderId;
    }

    public record ResourceDefinition(String uri, String name, String description, String mimeType) {
    }
}
