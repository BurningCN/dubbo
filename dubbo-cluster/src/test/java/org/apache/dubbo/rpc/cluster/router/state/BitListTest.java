package org.apache.dubbo.rpc.cluster.router.state;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * @author BurningCN
 * @date 2021/8/4 18:03
 */
public class BitListTest {
    @Test
    public void test() {
        List<String> list = Arrays.asList("A", "B", "C");
        BitList<String> bitList = new BitList<>(list);
        Assertions.assertEquals(bitList.getUnmodifiableList(), list);
        Assertions.assertEquals(3, bitList.size());

        Assertions.assertEquals("A", bitList.get(0));
        Assertions.assertEquals("B", bitList.get(1));
        Assertions.assertEquals("C", bitList.get(2));

        Assertions.assertTrue(bitList.contains("A"));
        Assertions.assertTrue(bitList.contains("B"));
        Assertions.assertTrue(bitList.contains("C"));

        Iterator<String> iterator = bitList.iterator();
        while (iterator.hasNext()) {
            String str = iterator.next();
            Assertions.assertTrue(list.contains(str));
        }

        Assertions.assertEquals(0, bitList.indexOf("A"));
        Assertions.assertEquals(1, bitList.indexOf("B"));
        Assertions.assertEquals(2, bitList.indexOf("C"));

        Object[] objects = bitList.toArray();
        for (Object obj : objects) {
            Assertions.assertTrue(list.contains(obj));
        }

        Object[] newObjects = new Object[1];
        Object[] copiedList = bitList.toArray(newObjects);
        Assertions.assertEquals(copiedList.length, 3);
        Assertions.assertArrayEquals(copiedList, list.toArray());

        newObjects = new Object[10];
        copiedList = bitList.toArray(newObjects);
        Assertions.assertNull(copiedList);

        bitList.remove(0);
        Assertions.assertEquals("B", bitList.get(0));
        bitList.addIndex(0);
        Assertions.assertEquals("A", bitList.get(0));

        bitList.removeAll(list);
        Assertions.assertEquals(0, bitList.size());
        bitList.clear();
    }

    @Test
    public void testIntersect() {
        List<String> aList = Arrays.asList("A", "B", "C");
        List<String> bList = Arrays.asList("A", "B");
        List<String> totalList = Arrays.asList("A", "B", "C");

        BitList<String> aBitList = new BitList<>(aList);
        BitList<String> bBitList = new BitList<>(bList);

        BitList<String> intersectBitList = aBitList.intersect(bBitList, totalList);
        Assertions.assertEquals(intersectBitList.size(), 2);
        Assertions.assertEquals(intersectBitList.get(0), totalList.get(0));
        Assertions.assertEquals(intersectBitList.get(1), totalList.get(1));
    }


}
