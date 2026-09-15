package testdomain;

public class Order {
    public String id;
    public String product;
    public int quantity;

    public Order() {
    }

    public Order(String id, String product, int quantity) {
        this.id = id;
        this.product = product;
        this.quantity = quantity;
    }
}
