getName()返回的是虚拟机里面的class的表示

getCanonicalName()返回的是更容易理解的表示

 

对于普通类来说,二者没什么区别,只是对于特殊的类型上有点表示差异

 

比如byte[]类型,前者就是[B,后者就是byte[]

比如byte[][]类型,前者就是[[B,后者就是byte[][]



=======

对于一般的type来说，这二者没有区别，对于array和inner type，就有区别了，可以写代码亲测，如下：

复制代码
 1 package simple;
 2 
 3 class Box {
 4     class Inner {}
 5 }
 6 
 7 public class Foo {
 8     public static void main(String[] args) throws Exception {
 9         // Ordinary class:
10         System.out.println(Box.class.getCanonicalName());
11         System.out.println(Box.class.getName());
12         // Inner class:
13         System.out.println(Box.Inner.class.getCanonicalName());
14         System.out.println(Box.Inner.class.getName());
15         // Array type:
16         System.out.println(args.getClass().getCanonicalName());
17         System.out.println(args.getClass().getName());
18     }
19 }
复制代码
一般，用于load class的时候，比如说Class.forName，就需要用Class.getName而不是Class.getCononicalName