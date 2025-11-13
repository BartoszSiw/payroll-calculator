package pl.edashi.converter.service;

import pl.edashi.converter.model.ConversionResult;

public class ConverterService {

    public ConversionResult convertCurrency(double amount, double rate) {
        double result = amount * rate;
        return new ConversionResult(amount, rate, result, "currency");
    }

    public ConversionResult convertLength(double meters) {
        double result = meters * 3.28084; // meters → feet
        return new ConversionResult(meters, 3.28084, result, "length");
    }
}

