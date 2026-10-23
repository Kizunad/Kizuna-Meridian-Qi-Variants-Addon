package dev.kizuna.meridian.qivariants.server;

/** 目录和查询错误保留相关身份，框架自身的拒绝保持原样。 */
final class QiVariantsErrors {
    private QiVariantsErrors() {
        // 工具类不持有目录或宿主状态。
    }

    static void require(boolean condition, String message, String subject) {
        // 所有校验在交付不可变目录或结果之前完成。
        if (!condition) {
            throw new IllegalArgumentException(message + "：" + subject);
        }
    }

    static IllegalArgumentException unknown(String id) {
        // 未知属性不能被默认为五行根。
        return new IllegalArgumentException("未知真元属性：" + id);
    }
}
