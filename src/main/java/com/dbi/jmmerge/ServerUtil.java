/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.dbi.jmmerge;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;

/**
 *
 * @author nnels2
 */
public class ServerUtil {
  @Value("#{systemProperties['jmmerge.data.dir'] ?: '/var/data/jmmerge'}")
  private String storageDir;
  
  public List<String> getServerList()
  {
    List<String> ret = new ArrayList<>();
    for(File file : new File(storageDir).listFiles())
    {
      if(file.isDirectory()) ret.add(file.getName());
    }
    return ret;
  }
}
