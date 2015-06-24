import java.util.ArrayList;
import java.io.Serializable;

public class Branch implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private CommitNode head;
    private ArrayList<Branch> subranches;

    /** Constructor for Branch. */
    public Branch(CommitNode bhead, String bname) {
        this.head = bhead;
        this.name = bname;
        this.subranches = new ArrayList<Branch>();
    }

    /** Adds subranch with given name and head to THIS. */
    public void addSubranch(CommitNode bhead, String bname) {
        Branch b = new Branch(bhead, bname);
        subranches.add(b);
    }

    /** Adds subranch b to THIS. */
    public void addSubranch(Branch b) {
        subranches.add(b);
    }

    /** Sets the head for THIS. */
    public void setHead(CommitNode c) {
        this.head = c;
    }

}
