package com.vova9110.bloodbath.alarmScreenBackground.StftFilter;

import java.util.LinkedList;
import java.util.List;

public class Windows {
    private final static String[] funcNames = new String[]{"rect", "hann", "hamming", "blackman", "nuttall", "blackman-nuttall", "blackman-harris", "flat-top"};
    private final static String[] fancyNames = new String[]{"Rectangular", "Hann", "Hamming", "Blackman", "Nuttall", "Blackman-Nuttall", "Blackman-Harris", "Flat top"};

    public static double[] applyWindow(double[] samples, String funcName){
        LinkedList<String> names = new LinkedList<>(List.of(funcNames));
        int nameNum=-1;
        for (String name:
             names) {
            if (name.matches(funcName)){
                nameNum = names.indexOf(name);
                break;
            }
        }
        switch (nameNum) {
            case (0):
                return samples;
            case (1):
                return applyHann(samples);
            case (2):
                return applyHamming(samples);
            case (3):
                return applyBlackman(samples);
            case (4):
                return applyNuttall(samples);
            case (5):
                return applyBlackmanNuttall(samples);
            case (6):
                return applyBlackmanHarris(samples);
            case (7):
                return applyFlatTop(samples);
            default:
                throw new IllegalArgumentException("This function not implemented yet");
        }
    }
    private static double[] applyHann(double[] samples){
        double[] result = new double[samples.length];
        int size = samples.length;
        for (int i = 0; i < size; i++) {
            double deltaTheta = 2*Math.PI * i / (size - 1);
            double val = .5 - .5 * Math.cos(deltaTheta);
            result[i] = val * samples[i];
        }
        return result;
    }
    private static double[] applyHamming(double[] samples){
        double[] result = new double[samples.length];
        int size = samples.length;
        for (int i = 0; i < size; i++) {
            double deltaTheta = 2*Math.PI * i / (size - 1);
            double val = .54 - .46 * Math.cos(deltaTheta);
            result[i] = val * samples[i];
        }
        return result;
    }
    private static double[] applyBlackman(double[] samples){
        double[] result = new double[samples.length];
        int size = samples.length;
        for (int i = 0; i < size; i++) {
            double a0=0.42;
            double a1=0.5;
            double a2=0.08;
            double deltaTheta = 2*Math.PI * i / (size - 1);
            double val = a0 - a1*Math.cos(deltaTheta) + a2*Math.cos(2*deltaTheta);
            result[i] = val * samples[i];
        }
        return result;
    }
    private static double[] applyNuttall(double[] samples){
        double[] result = new double[samples.length];
        int size = samples.length;
        for (int i = 0; i < size; i++) {
            double a0=0.3558;
            double a1=0.4874;
            double a2=0.1442;
            double a3=0.0126;
            double deltaTheta = 2*Math.PI * i / (size - 1);
            double val = a0 - a1*Math.cos(deltaTheta) + a2*Math.cos(2*deltaTheta) - a3*Math.cos(3*deltaTheta);
            result[i] = val * samples[i];
        }
        return result;
    }
    private static double[] applyBlackmanNuttall(double[] samples){
        double[] result = new double[samples.length];
        int size = samples.length;
        for (int i = 0; i < size; i++) {
            double a0=0.3636;
            double a1=0.4892;
            double a2=0.1366;
            double a3=0.0101;
            double deltaTheta = 2*Math.PI * i / (size - 1);
            double val = a0 - a1*Math.cos(deltaTheta) + a2*Math.cos(2*deltaTheta) - a3*Math.cos(3*deltaTheta);
            result[i] = val * samples[i];
        }
        return result;
    }
    private static double[] applyBlackmanHarris(double[] samples){
        double[] result = new double[samples.length];
        int size = samples.length;
        for (int i = 0; i < size; i++) {
            double a0=0.3559;
            double a1=0.4883;
            double a2=0.1413;
            double a3=0.0117;
            double deltaTheta = 2*Math.PI * i / (size - 1);
            double val = a0 - a1*Math.cos(deltaTheta) + a2*Math.cos(2*deltaTheta) - a3*Math.cos(3*deltaTheta);
            result[i] = val * samples[i];
        }
        return result;
    }
    private static double[] applyFlatTop(double[] samples){
        double[] result = new double[samples.length];
        int size = samples.length;
        for (int i = 0; i < size; i++) {
            double a0=0.2156;
            double a1=0.4166;
            double a2=0.2773;
            double a3=0.0836;
            double a4=0.0069;
            double deltaTheta = 2*Math.PI * i / (size - 1);
            double val = a0 - a1*Math.cos(deltaTheta) + a2*Math.cos(2*deltaTheta) - a3*Math.cos(3*deltaTheta) + a4*Math.cos(4*deltaTheta);
            result[i] = val * samples[i];
        }
        return result;
    }

    public static String[] getFuncNames(){return funcNames;}
    public static String[] getFancyNames(){return fancyNames;}
}
