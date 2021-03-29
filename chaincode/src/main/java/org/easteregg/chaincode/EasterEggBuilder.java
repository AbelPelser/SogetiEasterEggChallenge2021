package org.easteregg.chaincode;

import lombok.RequiredArgsConstructor;

/**
 * Draws an easter egg through a modified ellipse equation: (x−a)²/rx² + (y−b)²/ry² = 1
 * <p>
 * Modified ellipse equation for vertical egg: (x−centerXPoint)²*1000/(horizontalRadius² * (1+(0.025 * y))) +
 * (y−centerYPoint)²*1000/verticalRadius² = 1000 x, y -> coordinates of a single point on ellipse
 */
@RequiredArgsConstructor
public class EasterEggBuilder {

    private static final float EGG_EQUATION_SCALE_FACTOR = 1000;
    // 15 - 50
//    private static final double EGG_COLOR_FACTOR = 15;
    // 0.2 - 2
    // 7.5 < Power * factor < 15
//    private static final double EGG_COLOR_POWER_FACTOR = 0.3;

    private final EggMetrics eggMetrics;
    private final double colorFactor;
    private final double colorPowerFactor;


    public String build() {
        StringBuilder builder = new StringBuilder();
        for (int yCoordinate = 0; yCoordinate <= eggMetrics.getFrameHeight(); yCoordinate++) {
            for (int xCoordinate = 0; xCoordinate <= eggMetrics.getFrameWidth(); xCoordinate++) {
                builder.append(getEggPoint(yCoordinate, xCoordinate));
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    private Color getColor(double eggRatio) {
        Color[] allColors = Color.values();
        double eggRatioPowered = Math.pow(eggRatio, colorPowerFactor);
        int index = (int) (eggRatioPowered * allColors.length * colorFactor) % allColors.length;
        return allColors[index];
    }

    private String getEggPoint(int yCoordinate, int xCoordinate) {
        double eggRatio = getEggRatio(xCoordinate, yCoordinate);
        return eggRatio < 1.0 ? getColor(eggRatio).getColor() : eggMetrics.getBackgroundColor();
    }

    private double getEggRatio(int xCoordinate, int yCoordinate) {
        double numeratorSummand1 =
            (calculateSquareOfDistance(eggMetrics.getCenterXPoint(), xCoordinate) * EGG_EQUATION_SCALE_FACTOR) / (
                calculateSquare(eggMetrics.getHorizontalRadius()) * factorToChangeToEggShape(yCoordinate));
        double numeratorSummand2 =
            (calculateSquareOfDistance(eggMetrics.getCenterYPoint(), yCoordinate) * EGG_EQUATION_SCALE_FACTOR)
                / calculateSquare(eggMetrics.getVerticalRadius());
        return (numeratorSummand1 + numeratorSummand2) / EGG_EQUATION_SCALE_FACTOR;
    }

    private static int calculateSquareOfDistance(int centerPoint, int coordinate) {
        return calculateSquare(coordinate - centerPoint);
    }

    private static double factorToChangeToEggShape(int yCoordinate) {
        return 1 + (0.025 * yCoordinate);
    }

    private static int calculateSquare(int radius) {
        return radius * radius;
    }
}
