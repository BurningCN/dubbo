package netty.server;

/**
 * @author geyu
 * @date 2021/1/28 19:54
 */
public class Bytes {

    public static byte[] short2bytes(short v) {
        byte[] ret = new byte[2];
        short2bytes(v, ret);
        return ret;
    }

    public static void short2bytes(short v, byte[] b) {
        short2bytes(v, b, 0);
    }

    public static void short2bytes(short v, byte[] b, int off) {
        b[off + 1] = (byte) v;
        b[off + 0] = (byte) (v >>> 8);
    }

    public static void long2bytes(long v, byte[] b, int off) {
        b[off + 7] = (byte) v;
        b[off + 6] = (byte) (v >>> 8 * 1);
        b[off + 5] = (byte) (v >>> 8 * 2);
        b[off + 4] = (byte) (v >>> 8 * 3);
        b[off + 3] = (byte) (v >>> 8 * 4);
        b[off + 2] = (byte) (v >>> 8 * 5);
        b[off + 1] = (byte) (v >>> 8 * 6);
        b[off + 0] = (byte) (v >>> 8 * 7);// 最高位
    }

    public static void int2Bytes(int v, byte[] b, int off) {
        b[off + 3] = (byte) v;
        b[off + 2] = (byte) (v >>> 8 * 1);
        b[off + 1] = (byte) (v >>> 8 * 2);
        b[off + 0] = (byte) (v >>> 8 * 3); // 最高位
    }

    public static int bytes2int(byte[] b, int off) {
        return (b[off + 3] & 0xff << 0) +
                (b[off + 2] & 0xff << 8) +
                (b[off + 1] & 0xff << 16) +
                (b[off + 0] & 0xff << 24);
    }

    public static long bytes2long(byte[] b, int off) {
        return ((b[off + 7] & 0xFFL) << 0) +
                ((b[off + 6] & 0xFFL) << 8) +
                ((b[off + 5] & 0xFFL) << 16) +
                ((b[off + 4] & 0xFFL) << 24) +
                ((b[off + 3] & 0xFFL) << 32) +
                ((b[off + 2] & 0xFFL) << 40) +
                ((b[off + 1] & 0xFFL) << 48) +
                (((long) b[off + 0]) << 56);
    }

    public static void main(String[] args) {
        byte[] x = new byte[8];
        long2bytes(99999, x, 0);
        System.out.println(bytes2long(x, 0));
    }
}
