package com.ferry.util;

/**
 * 实名信息校验工具（对标 12306 的实名购票要求）：
 * - 姓名：非空，2~20 字符
 * - 身份证：18 位，前 17 位数字，末位数字或 X；并用 GB11643 校验位算法验证真伪
 * - 手机号：11 位，1 开头
 * 说明：本系统无公安联网核查接口，只能做格式与校验位层面的验证，
 *       不能保证"身份证一定是本人"，但对随意乱填的数字串可以有效拦截。
 */
public final class IdentityUtil {

    private IdentityUtil() { }

    /** 校验姓名：非空、2~20 字符 */
    public static boolean validName(String name) {
        if (name == null) return false;
        String s = name.trim();
        return s.length() >= 2 && s.length() <= 20;
    }

    /** 校验手机号：11 位数字且以 1 开头 */
    public static boolean validPhone(String phone) {
        return phone != null && phone.trim().matches("1\\d{10}");
    }

    /** 校验身份证：格式 + GB11643 校验位 */
    public static boolean validIdCard(String idCard) {
        if (idCard == null) return false;
        String s = idCard.trim().toUpperCase();
        if (!s.matches("\\d{17}[0-9X]")) return false;
        int[] weight = { 7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2 };
        char[] check = { '1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2' };
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (s.charAt(i) - '0') * weight[i];
        }
        return check[sum % 11] == s.charAt(17);
    }

    /** 返回第一条错误信息，全部合法返回 null */
    public static String validate(String name, String idCard, String phone) {
        if (!validName(name)) return "乘客姓名需为 2~20 个字符";
        if (!validIdCard(idCard)) return "身份证号格式或校验位不正确（需 18 位真实身份证号）";
        if (!validPhone(phone)) return "手机号需为 11 位且以 1 开头";
        return null;
    }
}
