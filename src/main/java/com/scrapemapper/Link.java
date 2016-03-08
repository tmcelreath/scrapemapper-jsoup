package com.scrapemapper;

/**
 * Created by 162717 on 3/8/16.
 */
public class Link {

    private String text;
    private String href;
    private LinkType linkType;


    public Link(String href, String text) {
        this.href=href;
        this.text=text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public LinkType getLinkType() {
        return linkType;
    }

    public void setLinkType(LinkType linkType) {
        this.linkType = linkType;
    }
}
