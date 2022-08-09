package org.acme.edge;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
public class Msg {
    private String sensor;
    private double pressure;
    private double temperature;
    private double humidity;
    private double gas_resistance;
    private double altitude;
    private List<Double> gps;
    private int co2;
    private int ppm;


    public Msg() {
    }

    public Msg(String sensor, double pressure, double temperature, double humidity, double gas_resistance, double altitude, List<Double> gps, int co2, int ppm) {
        this.sensor = sensor;
        this.pressure = pressure;
        this.temperature = temperature;
        this.humidity = humidity;
        this.gas_resistance = gas_resistance;
        this.altitude = altitude;
        this.gps = gps;
        this.co2 = co2;
        this.ppm = ppm;
    }

    public String getSensor() {
        return this.sensor;
    }

    public void setSensor(String sensor) {
        this.sensor = sensor;
    }

    public double getPressure() {
        return this.pressure;
    }

    public void setPressure(double pressure) {
        this.pressure = pressure;
    }

    public double getTemperature() {
        return this.temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getHumidity() {
        return this.humidity;
    }

    public void setHumidity(double humidity) {
        this.humidity = humidity;
    }

    public double getGas_resistance() {
        return this.gas_resistance;
    }

    public void setGas_resistance(double gas_resistance) {
        this.gas_resistance = gas_resistance;
    }

    public double getAltitude() {
        return this.altitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    public List<Double> getGps() {
        return this.gps;
    }

    public void setGps(List<Double> gps) {
        this.gps = gps;
    }

    public int getCo2() {
        return this.co2;
    }

    public void setCo2(int co2) {
        this.co2 = co2;
    }

    public int getPpm() {
        return this.ppm;
    }

    public void setPpm(int ppm) {
        this.ppm = ppm;
    }

    

    @Override
    public String toString() {
        return "{" +
            " sensor='" + getSensor() + "'" +
            ", pressure='" + getPressure() + "'" +
            ", temperature='" + getTemperature() + "'" +
            ", humidity='" + getHumidity() + "'" +
            ", gas_resistance='" + getGas_resistance() + "'" +
            ", altitude='" + getAltitude() + "'" +
            ", gps='" + getGps() + "'" +
            ", CO2='" + getCo2() + "'" +
            ", ppm='" + getPpm() + "'" +
            "}";
    }

}
