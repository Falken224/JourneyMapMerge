/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.dbi.jmmerge;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 *
 * @author nnels2
 */
public class PNGMerge {
  public static BufferedImage mergeImages(File newImage, File oldImage) throws IOException
  {
    File topImage = newImage.lastModified()>oldImage.lastModified()?newImage:oldImage;
    File bottomImage = newImage.lastModified()>oldImage.lastModified()?oldImage:newImage;
    BufferedImage top = ImageIO.read(topImage);
    BufferedImage bottom = ImageIO.read(bottomImage);
    BufferedImage merged = new BufferedImage(bottom.getWidth(), bottom.getHeight(), BufferedImage.TYPE_INT_ARGB);

    Graphics2D g = (Graphics2D)merged.getGraphics();
    g.drawImage(bottom, 0, 0, null);
    g.drawImage(top, 0, 0, null);
    return merged;
  }
  
  public static BufferedImage blackBackgroundedImage(BufferedImage image) throws IOException
  {
    BufferedImage newImg = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = (Graphics2D)newImg.getGraphics();
    g.drawImage(image, 0, 0, Color.BLACK,null);
    return newImg;
  }
}
