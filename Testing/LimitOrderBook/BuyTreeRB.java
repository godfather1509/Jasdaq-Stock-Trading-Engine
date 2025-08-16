package LimitOrderBook;
// import java.util.*;

public class BuyTreeRB {
  // best buy: buyer willing to pay highest price

  /*
   * Rules of Red Black Tree
   * 1. Every node is either red or black
   * 2. Root is always black
   * 3. No 2 cosecutive red nodes allowed, red child cannot have red parent and
   * vice versa
   * 4. Number of black nodes from every node to all its descendent leaves should
   * be same
   * 
   * Notes:
   * - A Red-Black Tree is less strict than an AVL tree in balancing height.
   * - The height of the longest path can be at most twice that of the shortest
   * path.
   * - Because of this relaxed balancing, insertions and deletions are generally
   * faster than in AVL trees.
   * 
   * time complexity: search, insert, delete O(logn)
   * space complexity: O(n)
   */

  Limit root;

  public void insert(Limit newLimit) {

    root = insert(newLimit, root);
  }

  public Limit insert(Limit newLimit, Limit node) {

    if (node == null && root == null) {
      root.color = "B";
      return newLimit;
    }

    if (node == null) {
      return newLimit;
    }

    if (newLimit.getPrice() > node.getPrice()) {
      node.right = insert(newLimit, node.right);
    }

    else if (newLimit.getPrice() < node.getPrice()) {
      node.left = insert(newLimit, node.left);
    }
    node.height = Math.max(node.left.height, node.right.height);

    return node;

  }

}
