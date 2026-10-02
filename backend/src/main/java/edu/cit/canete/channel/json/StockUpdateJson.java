package edu.cit.canete.channel.json;

public class StockUpdateJson {
    public String sellerSku;
    public int available;

    public StockUpdateJson(String sellerSku, int available) {
        this.sellerSku = sellerSku;
        this.available = available;
    }
}