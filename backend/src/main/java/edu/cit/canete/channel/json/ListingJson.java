package edu.cit.canete.channel.json;

public class ListingJson {
    public String sellerSku;
    public String title;
    public String supplierSku;

    public ListingJson(String sellerSku, String title, String supplierSku) {
        this.sellerSku = sellerSku;
        this.title = title;
        this.supplierSku = supplierSku;
    }
}