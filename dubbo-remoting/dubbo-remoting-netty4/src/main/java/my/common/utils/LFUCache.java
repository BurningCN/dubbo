/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package my.common.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 提到缓存，有两点是必须要考虑的：
 * （1）缓存数据和目标数据的一致性问题。
 * （2）缓存的过期策略（机制）。
 *      其中，缓存的过期策略涉及淘汰算法。常用的淘汰算法有下面几种：
 * （1）FIFO：First In First Out，先进先出
 * （2）LRU：Least Recently Used，最近最少使用
 * （3）LFU：Least Frequently Used，最不经常使用
 *       注意LRU和LFU的区别。LFU算法是根据在一段时间里数据项被使用的次数选择出最少使用的数据项，即根据使用次数的差异来决定。而LRU是根据使用时间的差异来决定的。
 *         一个优秀的缓存框架必须实现以上的所有缓存机制。例如：Ehcache就实现了上面的所有策略。
 * @param <K>
 * @param <V>
 */
// OK
public class LFUCache<K, V> {

    // LRU和LFU都是需要两个容器，map从o1的快速访问，队列实现LFU的F或者LRU的R
    private Map<K, CacheNode<K, V>> map;
    private CacheDeque<K, V>[] freqTable;

    private final int capacity;
    private int evictionCount;
    private int curSize = 0;

    // 用以实现线程安全
    private final ReentrantLock lock = new ReentrantLock();
    private static final int DEFAULT_LOAD_FACTOR = 1000;

    private static final float DEFAULT_EVICTION_CAPACITY = 0.75f;

    public LFUCache() {
        // 使用默认的容量和驱逐因子大小
        this(DEFAULT_LOAD_FACTOR, DEFAULT_EVICTION_CAPACITY);
    }

    /**
     * Constructs and initializes cache with specified capacity and eviction(驱逐、赶出)
     * factor. Unacceptable parameter values followed with
     * {@link IllegalArgumentException}.
     *
     * @param maxCapacity    cache max capacity
     * @param evictionFactor cache proceedEviction factor
     */
    public LFUCache(final int maxCapacity, final float evictionFactor) {
        // 最大容量校验
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " +
                    maxCapacity);
        }
        // 驱逐阈值率校验，注意这个参数不是说存储空间的阈值为 evictionFactor * maxCapacity，存储的阈值一直是maxCapacity
        // 只是阈值到达之后，还put新的元素，会驱逐一些元素，驱逐的元素个数就是evictionFactor控制的，详见下面的evictionCount
        boolean factorInRange = evictionFactor <= 1 || evictionFactor < 0;
        if (!factorInRange || Float.isNaN(evictionFactor)) {
            throw new IllegalArgumentException("Illegal eviction factor value:"
                    + evictionFactor);
        }
        // 最大空间
        this.capacity = maxCapacity;
        // 驱逐个数。假设最大空间是8，驱逐个数为6。存了8个元素，再put一个新的元素的时候，超过最大空间，那么就会驱除元素，驱逐到最后只剩两个元素（8-6）
        this.evictionCount = (int) (capacity * evictionFactor);
        // 存储kv的容器，用来控制o1级别的快速访问
        this.map = new HashMap<>();
        // 也是存储kv的容器，但是是用来做LFU的，以及容量满了，执行驱逐策略，淘汰访问次数最少的元素（根据使用次数的差异来决定，LRU是根据时间）
        this.freqTable = new CacheDeque[capacity + 1];
        // 准备capacity+1个队列
        for (int i = 0; i <= capacity; i++) {
            // 构造CacheDeque队列的时候，默认有两个节点分别是first和last，并双向指向，进去
            freqTable[i] = new CacheDeque<K, V>();
        }
        // 利用nextDeque指针让队列连接
        for (int i = 0; i < capacity; i++) {
            freqTable[i].nextDeque = freqTable[i + 1];
        }
        // 最后一个队列和自己连接
        freqTable[capacity].nextDeque = freqTable[capacity];
    }

    public int getCapacity() {
        return capacity;
    }

    public V put(final K key, final V value) {
        CacheNode<K, V> node;
        // 加锁，线程安全的LFUCache
        lock.lock();
        try {
            if (map.containsKey(key)) {
                // 从map取出
                node = map.get(key);
                if (node != null) {
                    // 从队列中移除，node的前后节点连接下即可
                    CacheNode.withdrawNode(node);
                }
                // 覆盖value值
                node.value = value;
                // 再存到第0个队列最后
                freqTable[0].addLastNode(node);
                // 再存到map（覆盖）
                map.put(key, node);
            } else {
                // 一般进这个分支

                // 将元素添加到第0个队列的最后，进去
                node = freqTable[0].addLast(key, value);
                // 填充到map
                map.put(key, node);
                // 个数++
                curSize++;
                // 超过阈值，执行驱逐
                if (curSize > capacity) {
                    // 进去
                    proceedEviction();
                }
            }
        } finally {
            lock.unlock();
        }
        return node.value;
    }

    public V remove(final K key) {
        CacheNode<K, V> node = null;
        // remove写操作，防止并发修改出现数据不一致，需要加锁
        lock.lock();
        try {
            if (map.containsKey(key)) {
                // 从map移除
                node = map.remove(key);
                if (node != null) {
                    // 从队列移除，静态方法，因为node里面存储了前后指针
                    CacheNode.withdrawNode(node);
                }
                // 个数 --
                curSize--;
            }
        } finally {
            lock.unlock();
        }
        return (node != null) ? node.value : null;
    }

    // get操作是核心，涉及到"频率、次数"
    public V get(final K key) {
        CacheNode<K, V> node = null;
        // 读写都加锁
        lock.lock();
        try {
            if (map.containsKey(key)) {
                node = map.get(key);
                // 从原有队列移除
                CacheNode.withdrawNode(node);
                // 放到下一个队列中最后（根据nextDeque取到连接的下一个队列） ---- > 访问的次数越多，这个node所在的队列就越靠后
                node.owner.nextDeque.addLastNode(node);
            }
        } finally {
            lock.unlock();
        }
        return (node != null) ? node.value : null;
    }

    /**
     * Evicts less frequently used elements corresponding to eviction factor,
     * specified at instantiation step.
     *
     * @return number of evicted elements
     */
    private int proceedEviction() {
        // eg ： 8-6  = 2 ，那么就需要一直驱逐直到剩余2个元素为止
        int targetSize = capacity - evictionCount;
        int evictedElements = 0;

        FREQ_TABLE_ITER_LOOP:
        // 从头(1)遍历所有的CacheDeque队列
        for (int i = 0; i <= capacity; i++) {
            CacheNode<K, V> node;
            while (!freqTable[i].isEmpty()) {
                // 头部(2)弹出第一个
                node = freqTable[i].pollFirst();
                // ★两处提到头部，为什么从第0个队列的第头部元素开始驱逐呢？因为是按照LFU的！在我们每次put的时候，一直是只存到第0个队列里，但是一旦
                // 发生get操作，那么就会将此Node从第0个队列迁移到第1个队列的最后位置，所以越前面的队列，队列中越靠头的节点最需要被淘汰。
                // 注意：这里不一定就是从第0个队列迁移到第1个队列，因为get方法对应的Node可能在第n个队列（说明之前get过多次。
                // 不过这个node第1次get肯定是第0个队列迁移到第1个），那么就会把node从n迁移到第n+1个队列 ，详见get方法）。

                // 进去
                remove(node.key);
                // 是否驱逐完毕
                if (targetSize >= curSize) {
                    break FREQ_TABLE_ITER_LOOP;
                }
                evictedElements++;
            }
        }
        return evictedElements;
    }

    /**
     * Returns cache current size.
     *
     * @return cache size
     */
    public int getSize() {
        return curSize;
    }

    static class CacheNode<K, V> {

        CacheNode<K, V> prev;
        CacheNode<K, V> next;
        K key;
        V value;
        CacheDeque owner;

        CacheNode() {
        }

        CacheNode(final K key, final V value) {
            this.key = key;
            this.value = value;
        }

        /**
         * This method takes specified node and reattaches it neighbors nodes
         * links to each other, so specified node will no longer tied with them.
         * Returns united node, returns null if argument is null.
         *
         * 此方法接受指定的节点，并将它的邻居节点链接重新连接到彼此，因此指定的节点将不再与它们绑定。
         * 返回联合节点，如果参数为null则返回null。
         *
         * @param node note to retrieve
         * @param <K>  key
         * @param <V>  value
         * @return retrieved node
         */
        static <K, V> CacheNode<K, V> withdrawNode(
                final CacheNode<K, V> node) {
            if (node != null && node.prev != null) {
                node.prev.next = node.next;
                if (node.next != null) {
                    node.next.prev = node.prev;
                }
            }
            return node;
        }

    }

    /**
     * Custom deque implementation of LIFO type. Allows to place element at top
     * of deque and poll very last added elements. An arbitrary node from the
     * deque can be removed with {@link CacheNode#withdrawNode(CacheNode)}
     * method.
     *
     * 自定义deque实现LIFO类型。允许将元素放置在deque的顶部，并poll最后添加的元素。可以通过{@link CacheNode#withdrawNode(CacheNode)}
     * 删除deque中的任意节点方法。
     * @param <K> key
     * @param <V> value
     */
    static class CacheDeque<K, V> {

        CacheNode<K, V> last;
        CacheNode<K, V> first;
        CacheDeque<K, V> nextDeque;

        /**
         * Constructs list and initializes last and first pointers.
         * 构造列表并初始化last和first指针。
         */
        CacheDeque() {
            last = new CacheNode<>();
            first = new CacheNode<>();
            last.next = first;
            first.prev = last;
        }

        /**
         * Puts the node with specified key and value at the end of the deque
         * and returns node.
         *
         * @param key   key
         * @param value value
         * @return added node
         */
        // 作用看上面注释
        CacheNode<K, V> addLast(final K key, final V value) {
            CacheNode<K, V> node = new CacheNode<>(key, value);
            // 这个记录着当前node属于哪个队列(CacheDeque)，因为node会在get操作进行迁移，隶属于不同的队列
            node.owner = this;
            // 下四个操作 就是将Node存到队列最后，相关指针进行连接的操作
            node.next = last.next;
            node.prev = last;
            node.next.prev = node;
            last.next = node;
            return node;
        }

        CacheNode<K, V> addLastNode(final CacheNode<K, V> node) {
            node.owner = this;
            node.next = last.next;
            node.prev = last;
            node.next.prev = node;
            last.next = node;
            return node;
        }

        /**
         * Retrieves and removes the first node of this deque.
         *
         * @return removed node
         */
        CacheNode<K, V> pollFirst() {
            CacheNode<K, V> node = null;
            if (first.prev != last) {
                node = first.prev;
                first.prev = node.prev;
                first.prev.next = first;
                node.prev = null;
                node.next = null;
            }
            return node;
        }

        /**
         * Checks if link to the last node points to link to the first node.
         *
         * @return is deque empty
         */
        boolean isEmpty() {
            return last.next == first;
        }

    }

}