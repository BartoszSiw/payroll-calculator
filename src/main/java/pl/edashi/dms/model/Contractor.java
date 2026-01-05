package pl.edashi.dms.model;

public class Contractor {
	public String id;
    public String nip;
    public String name1;
    public String name2;
    public String name3;
    public String country;
    public String region;
    public String district;
    public String city;
    public String zip;
    public String street;
    public String houseNumber;
    public Contractor() { }

    // --- Gettery ---
    public String getId() { return id; }
    public String getNip() { return nip; }
    public String getName1() { return name1; }
    public String getName2() { return name2; }
    public String getName3() { return name3; }
    public String getCountry() { return country; }
    public String getRegion() { return region; }
    public String getDistrict() { return district; }
    public String getCity() { return city; }
    public String getZip() { return zip; }
    public String getStreet() { return street; }
    public String getHouseNumber() { return houseNumber; }

    // --- Settery ---
    public void setId(String id) { this.id = id != null ? id : ""; }
    public void setNip(String nip) { this.nip = nip != null ? nip : ""; }
    public void setName1(String name1) { this.name1 = name1 != null ? name1 : ""; }
    public void setName2(String name2) { this.name2 = name2 != null ? name2 : ""; }
    public void setName3(String name3) { this.name3 = name3 != null ? name3 : ""; }
    public void setCountry(String country) { this.country = country != null ? country : ""; }
    public void setRegion(String region) { this.region = region != null ? region : ""; }
    public void setDistrict(String district) { this.district = district != null ? district : ""; }
    public void setCity(String city) { this.city = city != null ? city : ""; }
    public void setZip(String zip) { this.zip = zip != null ? zip : ""; }
    public void setStreet(String street) { this.street = street != null ? street : ""; }
    public void setHouseNumber(String houseNumber) { this.houseNumber = houseNumber != null ? houseNumber : ""; }
    @Override
    public String toString() {
        return "Contractor{" +
                "id='" + id + '\'' +
                ", nip='" + nip + '\'' +
                ", name1='" + name1 + '\'' +
                ", city='" + city + '\'' +
                ", zip='" + zip + '\'' +
                '}';
    }
}
