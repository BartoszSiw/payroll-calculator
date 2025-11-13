package pl.edashi.converter.model;

public class ConversionResult {
    private double input;
    private double factor;
    private double output;
    private String type;

    public ConversionResult(double input, double factor, double output, String type) {
        this.input = input;
        this.factor = factor;
        this.output = output;
        this.type = type;
    }

    public double getInput() { return input; }
    public double getFactor() { return factor; }
    public double getOutput() { return output; }
    public String getType() { return type; }
}

