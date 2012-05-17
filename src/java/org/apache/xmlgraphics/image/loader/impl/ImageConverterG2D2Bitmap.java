/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id: ImageConverterG2D2Bitmap.java 798806M 2012-05-15 08:52:01Z (local) $ */

package org.apache.xmlgraphics.image.loader.impl;

import ae.java.awt.Color;
import ae.java.awt.Graphics2D;
import ae.java.awt.Point;
import ae.java.awt.RenderingHints;
import ae.java.awt.Transparency;
import ae.java.awt.color.ColorSpace;
import ae.java.awt.geom.Rectangle2D;
import ae.java.awt.image.BufferedImage;
import ae.java.awt.image.ColorModel;
import ae.java.awt.image.ComponentColorModel;
import ae.java.awt.image.DataBuffer;
import ae.java.awt.image.Raster;
import ae.java.awt.image.WritableRaster;
import java.util.Map;

import org.apache.xmlgraphics.image.GraphicsConstants;
import org.apache.xmlgraphics.image.loader.Image;
import org.apache.xmlgraphics.image.loader.ImageFlavor;
import org.apache.xmlgraphics.image.loader.ImageProcessingHints;
import org.apache.xmlgraphics.image.loader.ImageSize;
import org.apache.xmlgraphics.util.UnitConv;

/**
 * This ImageConverter converts Graphics2D images to a BufferedImage.
 */
public class ImageConverterG2D2Bitmap extends AbstractImageConverter {

    /** {@inheritDoc} */
    public Image convert(Image src, Map hints) {
        checkSourceFlavor(src);
        ImageGraphics2D g2dImage = (ImageGraphics2D)src;

        Object formatIntent = hints.get(ImageProcessingHints.BITMAP_TYPE_INTENT);
        int bitsPerPixel = 24;
        if (ImageProcessingHints.BITMAP_TYPE_INTENT_GRAY.equals(formatIntent)) {
            bitsPerPixel = 8;
        } else if (ImageProcessingHints.BITMAP_TYPE_INTENT_MONO.equals(formatIntent)) {
            bitsPerPixel = 1;
        }

        Object transparencyIntent = hints.get(ImageProcessingHints.TRANSPARENCY_INTENT);
        boolean withAlpha = true;
        if (ImageProcessingHints.TRANSPARENCY_INTENT_IGNORE.equals(transparencyIntent)) {
            withAlpha = false;
        }

        int resolution = GraphicsConstants.DEFAULT_SAMPLE_DPI;
        Number res = (Number)hints.get(ImageProcessingHints.TARGET_RESOLUTION);
        if (res != null) {
            resolution = res.intValue();
        }

        BufferedImage bi = paintToBufferedImage(g2dImage, bitsPerPixel, withAlpha, resolution);

        ImageBuffered bufImage = new ImageBuffered(src.getInfo(), bi, null);
        return bufImage;
    }

    /**
     * Paints a Graphics2D image on a BufferedImage and returns this bitmap.
     * @param g2dImage the Graphics2D image
     * @param bitsPerPixel the desired number of bits per pixel (supported: 1, 8, 24)
     * @param withAlpha true if the generated image should have an alpha channel
     * @param resolution the requested bitmap resolution
     * @return the newly created BufferedImage
     */
    protected BufferedImage paintToBufferedImage(ImageGraphics2D g2dImage,
            int bitsPerPixel, boolean withAlpha, int resolution) {
        ImageSize size = g2dImage.getSize();

        RenderingHints additionalHints = null;
        int bmw = (int)Math.ceil(UnitConv.mpt2px(size.getWidthMpt(), resolution));
        int bmh = (int)Math.ceil(UnitConv.mpt2px(size.getHeightMpt(), resolution));
        BufferedImage bi;
        switch (bitsPerPixel) {
        case 1:
            bi = new BufferedImage(bmw, bmh, BufferedImage.TYPE_BYTE_BINARY);
            withAlpha = false;
            //withAlpha is ignored in this case
            additionalHints = new RenderingHints(null);
            //The following usually has no effect but some class libraries might support it
            additionalHints.put(RenderingHints.KEY_DITHERING,
                    RenderingHints.VALUE_DITHER_ENABLE);
            break;
        case 8:
            if (withAlpha) {
                bi = createGrayBufferedImageWithAlpha(bmw, bmh);
            } else {
                bi = new BufferedImage(bmw, bmh, BufferedImage.TYPE_BYTE_GRAY);
            }
            break;
        default:
            if (withAlpha) {
                bi = new BufferedImage(bmw, bmh, BufferedImage.TYPE_INT_ARGB);
            } else {
                bi = new BufferedImage(bmw, bmh, BufferedImage.TYPE_INT_RGB);
            }
        }

        Graphics2D g2d = bi.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            setRenderingHintsForBufferedImage(g2d);
            if (additionalHints != null) {
                g2d.addRenderingHints(additionalHints);
            }

            g2d.setBackground(Color.white);
            g2d.setColor(Color.black);
            if (!withAlpha) {
                g2d.clearRect(0, 0, bmw, bmh);
            }
            /* debug code
            int off = 2;
            g2d.drawLine(off, 0, off, bmh);
            g2d.drawLine(bmw - off, 0, bmw - off, bmh);
            g2d.drawLine(0, off, bmw, off);
            g2d.drawLine(0, bmh - off, bmw, bmh - off);
            */
            double sx = (double)bmw / size.getWidthMpt();
            double sy = (double)bmh / size.getHeightMpt();
            g2d.scale(sx, sy);

            //Paint the image on the BufferedImage
            Rectangle2D area = new Rectangle2D.Double(
                    0.0, 0.0, size.getWidthMpt(), size.getHeightMpt());
            g2dImage.getGraphics2DImagePainter().paint(g2d, area);
        } finally {
            g2d.dispose();
        }
        return bi;
    }

    private static BufferedImage createGrayBufferedImageWithAlpha(int width, int height) {
        BufferedImage bi;
        boolean alphaPremultiplied = true;
        int bands = 2;
        int[] bits = new int[bands];
        for (int i = 0; i < bands; i++) {
            bits[i] = 8;
        }
        ColorModel cm = new ComponentColorModel(
                ColorSpace.getInstance(ColorSpace.CS_GRAY),
                bits,
                true, alphaPremultiplied,
                Transparency.TRANSLUCENT,
                DataBuffer.TYPE_BYTE);
        WritableRaster wr = Raster.createInterleavedRaster(
                DataBuffer.TYPE_BYTE,
                width, height, bands,
                new Point(0, 0));
        bi = new BufferedImage(cm, wr, alphaPremultiplied, null);
        return bi;
    }

    /**
     * Sets rendering hints on the Graphics2D created for painting to a BufferedImage. Subclasses
     * can modify the settings to customize the behaviour.
     * @param g2d the Graphics2D instance
     */
    protected void setRenderingHintsForBufferedImage(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
    }

    /** {@inheritDoc} */
    public ImageFlavor getSourceFlavor() {
        return ImageFlavor.GRAPHICS2D;
    }

    /** {@inheritDoc} */
    public ImageFlavor getTargetFlavor() {
        return ImageFlavor.BUFFERED_IMAGE;
    }

}