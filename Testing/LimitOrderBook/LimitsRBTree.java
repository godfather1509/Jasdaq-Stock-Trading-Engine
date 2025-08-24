package LimitOrderBook;

// best buy: buyer willing to pay highest price
// best sell: person willing to sell at least price
/*

Rules of Red Black Tree
 1. Every node is either red or black
 2. Root is always black
 3. No 2 cosecutive red nodes allowed, red child cannot have red parent and
 vice versa
 4. Number of black nodes from every node to all its descendent leaves should
 be same
 
 Notes:
 - A Red-Black Tree is less strict than an AVL tree in balancing height.
 - The height of the longest path can be at most twice that of the shortest
 path.
 - Because of this relaxed balancing, insertions and deletions are generally
 faster than in AVL trees.
* time complexity: search, insert, delete O(logn)
* space complexity: O(n)

*/

public class LimitsRBTree {

  /** Root of the RB tree */
  private Limit root;
  private boolean buySell;

  public LimitsRBTree(boolean buySell) {
    // if its true then it is a buy tree else it is sell tree
    this.buySell = buySell;
    // buy true
    // sell false
  }

  public Limit bestPrice() {
    if (root != null) {
      Limit x = root;
      if (buySell) {
        while (x.right != null) {
          x = x.right;
        }
        return x;
      } else {
        while (x.left != null) {
          x = x.left;
        }
        return x;
      }
    } else {
      // System.out.println("Tree is Empty");
      return null;
    }
  }

  public Limit nextBestPrice() {
    // return next best price
    Limit limit = bestPrice();
    return limit.parent;
  }

  public boolean isEmpty() {
    // tells if tree is empty
    return root == null;
  }

  public void display() {
    if (root == null) {
      // System.out.println("Tree is empty");
      return;
    }
    display(root, "Root node");
  }

  public void display(Limit limit, String details) {
    if (limit == null) {
      return;
    }
    System.out.println(details + limit.getPrice());
    display(limit.right, "Right node:");
    display(limit.left, "Left node:");
  }

  /** Insert the given Limit node by price (new nodes are colored RED). */
  public void insert(Limit newLimit) {
    Limit z = newLimit;
    z.left = z.right = z.parent = null;
    z.color = "R"; // new nodes are RED by default

    // Standard BST insert
    Limit y = null;
    Limit x = root;
    while (x != null) {
      y = x;
      if (z.getPrice() < x.getPrice())
        x = x.left;
      else
        x = x.right;
    }
    z.parent = y;
    if (y == null) {
      root = z;
    } else if (z.getPrice() < y.getPrice()) {
      y.left = z;
    } else {
      y.right = z;
    }

    // Fix red-black properties
    fixInsert(z);
  }

  /**
   * Delete a node by matching price (if multiple equal prices exist, deletes the
   * one found by BST search).
   */
  public void delete(Limit targetByPrice) {
    // Find node z with the matching price
    Limit z = root;
    while (z != null && z.getPrice() != targetByPrice.getPrice()) {
      z = (targetByPrice.getPrice() < z.getPrice()) ? z.left : z.right;
    }
    if (z == null)
      return; // not found

    Limit y = z; // node that will actually be removed from the tree
    String yOriginalColor = y.color; // needed for fix-up
    Limit x; // child that replaces y (may be null)
    Limit xParent; // explicit parent for x (needed if x is null)

    if (z.left == null) {
      // replace z with its right child
      x = z.right;
      xParent = z.parent;
      transplant(z, z.right);
    } else if (z.right == null) {
      // replace z with its left child
      x = z.left;
      xParent = z.parent;
      transplant(z, z.left);
    } else {
      // two children: find successor y = minimum(z.right)
      y = minimum(z.right);
      yOriginalColor = y.color;
      x = y.right; // y has no left child by definition
      if (y.parent == z) {
        xParent = y; // if x is null, its parent is y after transplant
        if (x != null)
          x.parent = y;
      } else {
        xParent = y.parent;
        transplant(y, y.right);
        y.right = z.right;
        if (y.right != null)
          y.right.parent = y;
      }
      transplant(z, y);
      y.left = z.left;
      if (y.left != null)
        y.left.parent = y;
      y.color = z.color; // preserve original color at z's position
    }

    // If we removed a BLACK node, fix potential violations
    if ("B".equals(yOriginalColor)) {
      fixDelete(x, xParent);
    }
  }

  /* -------------------- Helpers: rotations & utilities -------------------- */

  private void rotateLeft(Limit x) {
    if (x == null)
      return;
    Limit y = x.right; // must be non-null for a valid rotate, caller ensures
    x.right = (y != null) ? y.left : null;
    if (y != null && y.left != null)
      y.left.parent = x;

    if (y != null)
      y.parent = x.parent;

    if (x.parent == null) {
      root = y;
    } else if (x == x.parent.left) {
      x.parent.left = y;
    } else {
      x.parent.right = y;
    }

    if (y != null)
      y.left = x;
    x.parent = y;
  }

  private void rotateRight(Limit x) {
    if (x == null)
      return;
    Limit y = x.left;
    x.left = (y != null) ? y.right : null;
    if (y != null && y.right != null)
      y.right.parent = x;

    if (y != null)
      y.parent = x.parent;

    if (x.parent == null) {
      root = y;
    } else if (x == x.parent.right) {
      x.parent.right = y;
    } else {
      x.parent.left = y;
    }

    if (y != null)
      y.right = x;
    x.parent = y;
  }

  private void transplant(Limit u, Limit v) {
    if (u.parent == null) {
      root = v;
    } else if (u == u.parent.left) {
      u.parent.left = v;
    } else {
      u.parent.right = v;
    }
    if (v != null)
      v.parent = u.parent;
  }

  private Limit minimum(Limit n) {
    while (n.left != null)
      n = n.left;
    return n;
  }

  /*
   * -------------------- Color helpers (null treated as BLACK)
   * --------------------
   */

  private boolean isRed(Limit n) {
    return n != null && "R".equals(n.color);
  }

  private boolean isBlack(Limit n) {
    return !isRed(n); // null -> BLACK
  }

  /* -------------------- Fix-up after insertion -------------------- */

  private void fixInsert(Limit z) {
    while (z.parent != null && isRed(z.parent)) {
      Limit parent = z.parent;
      Limit grand = parent.parent;
      if (grand == null)
        break; // parent is root; will enforce root black at end

      if (parent == grand.left) {
        Limit uncle = grand.right;

        // Case 1: uncle is RED -> recolor and move up the tree
        if (isRed(uncle)) {
          parent.color = "B";
          uncle.color = "B";
          grand.color = "R";
          z = grand;
        } else {
          // Case 2: inner (z is right child) -> rotate parent to convert to outer
          if (z == parent.right) {
            z = parent;
            rotateLeft(z);
            parent = z.parent;
            grand = parent.parent;
          }
          // Case 3: outer (z is left child) -> recolor and rotate grand
          parent.color = "B";
          grand.color = "R";
          rotateRight(grand);
        }

      } else {
        // Mirror side: parent is right child of grand
        Limit uncle = grand.left;

        if (isRed(uncle)) {
          parent.color = "B";
          uncle.color = "B";
          grand.color = "R";
          z = grand;
        } else {
          if (z == parent.left) {
            z = parent;
            rotateRight(z);
            parent = z.parent;
            grand = parent.parent;
          }
          parent.color = "B";
          grand.color = "R";
          rotateLeft(grand);
        }
      }
    }
    if (root != null)
      root.color = "B"; // root must be BLACK
  }

  /* -------------------- Fix-up after deletion -------------------- */

  private void fixDelete(Limit x, Limit xParent) {
    Limit cur = x;
    Limit parent = xParent;

    while (cur != root && isBlack(cur)) {
      if (cur == (parent != null ? parent.left : null)) {
        // -------- Left side --------
        Limit sib = (parent != null) ? parent.right : null;

        // Case A: sibling is RED -> rotate to make sibling BLACK
        if (isRed(sib)) {
          sib.color = "B";
          if (parent != null)
            parent.color = "R";
          rotateLeft(parent);
          sib = (parent != null) ? parent.right : null;
        }

        // Now sibling is BLACK
        // Case B: both of sibling's children are BLACK
        if (isBlack(sib != null ? sib.left : null) && isBlack(sib != null ? sib.right : null)) {
          if (sib != null)
            sib.color = "R";
          cur = parent;
          parent = (cur != null) ? cur.parent : null;
        } else {
          // Case C: sibling's right child is BLACK, left child is RED -> rotateRight(sib)
          if (isBlack(sib != null ? sib.right : null)) {
            if (sib != null && sib.left != null)
              sib.left.color = "B";
            if (sib != null)
              sib.color = "R";
            rotateRight(sib);
            sib = (parent != null) ? parent.right : null;
          }
          // Case D: sibling's right child is RED
          if (sib != null) {
            sib.color = isRed(parent) ? "R" : "B";
            if (sib.right != null)
              sib.right.color = "B";
          }
          if (parent != null)
            parent.color = "B";
          rotateLeft(parent);
          cur = root; // done
        }

      } else {
        // -------- Right side (mirror) --------
        Limit sib = (parent != null) ? parent.left : null;

        // Case A': sibling is RED
        if (isRed(sib)) {
          sib.color = "B";
          if (parent != null)
            parent.color = "R";
          rotateRight(parent);
          sib = (parent != null) ? parent.left : null;
        }

        // Case B': both children BLACK
        if (isBlack(sib != null ? sib.left : null) && isBlack(sib != null ? sib.right : null)) {
          if (sib != null)
            sib.color = "R";
          cur = parent;
          parent = (cur != null) ? cur.parent : null;
        } else {
          // Case C': sibling's left child is BLACK, right child is RED -> rotateLeft(sib)
          if (isBlack(sib != null ? sib.left : null)) {
            if (sib != null && sib.right != null)
              sib.right.color = "B";
            if (sib != null)
              sib.color = "R";
            rotateLeft(sib);
            sib = (parent != null) ? parent.left : null;
          }
          // Case D': sibling's left child is RED
          if (sib != null) {
            sib.color = isRed(parent) ? "R" : "B";
            if (sib.left != null)
              sib.left.color = "B";
          }
          if (parent != null)
            parent.color = "B";
          rotateRight(parent);
          cur = root; // done
        }
      }
    }

    if (cur != null)
      cur.color = "B";
  }
}