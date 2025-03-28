/*
 *  Copyright 2025 Budapest University of Technology and Economics
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package hu.bme.mit.theta.common.visualization;

import static com.google.common.base.Preconditions.checkNotNull;

import java.awt.Color;

public final class EdgeAttributes {

    private final String label;
    private final Color color;
    private final LineStyle lineStyle;
    private final String font;
    private final int weight;
    private final Alignment alignment;

    private EdgeAttributes(
            final String label,
            final Color color,
            final LineStyle lineStyle,
            final String font,
            final int weight,
            final Alignment alignment) {
        this.label = label;
        this.color = color;
        this.lineStyle = lineStyle;
        this.font = font;
        this.weight = weight;
        this.alignment = alignment;
    }

    public String getLabel() {
        return label;
    }

    public Color getColor() {
        return color;
    }

    public LineStyle getLineStyle() {
        return lineStyle;
    }

    public String getFont() {
        return font;
    }

    public int getWeight() {
        return weight;
    }

    public Alignment getAlignment() {
        return alignment;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String label = "";
        private Color color = Color.BLACK;
        private LineStyle lineStyle = LineStyle.NORMAL;
        private String font = "";
        private int weight = 1;
        private Alignment alignment = Alignment.CENTER;

        public Builder label(final String label) {
            this.label = checkNotNull(label);
            return this;
        }

        public Builder color(final Color color) {
            this.color = checkNotNull(color);
            return this;
        }

        public Builder lineStyle(final LineStyle lineStyle) {
            this.lineStyle = checkNotNull(lineStyle);
            return this;
        }

        public Builder font(final String font) {
            this.font = checkNotNull(font);
            return this;
        }

        public Builder weight(final int weight) {
            this.weight = weight;
            return this;
        }

        public Builder alignment(final Alignment alignment) {
            this.alignment = alignment;
            return this;
        }

        public EdgeAttributes build() {
            return new EdgeAttributes(label, color, lineStyle, font, weight, alignment);
        }
    }
}
