package com.example.sydneyinfo.model;

public class SydneyEvent {
    private String name;
    private String date;       // formatted display date
    private String time;       // formatted display time
    private String venue;
    private String genre;
    private String url;
    private String imageUrl;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
