/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.dbi.jmmerge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 *
 * @author nnels2
 */
@RestController
public class MapController {

  private Logger LOG = LoggerFactory.getLogger(MapController.class);
  
  @Value("#{systemProperties['jmmerge.data.dir'] ?: '/var/data/jmmerge'}")
  private String storageDir;
  
  @ExceptionHandler
  public ResponseEntity<Map> handleException(Exception ex, HttpServletRequest request)
  {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map msgs = new HashMap();
    ResponseEntity<Map> ret = new ResponseEntity<Map>(msgs, headers, HttpStatus.INTERNAL_SERVER_ERROR);
    msgs.put("message", "An error occurred . . . contact your administrator for details.");
    msgs.put("error",ex.getMessage());
    LOG.error("An error occurred handling a "+request.getMethod()+" to URL "+request.getRequestURL(),ex);
    return ret;
  }
  
  @RequestMapping(value="/maps/{server}/{dimension}/{level}/{x},{y}.png", produces="image/png")
  public byte[] grabImage(@PathVariable("server") String server,
                          @PathVariable("dimension") String dimension,
                          @PathVariable("level") String level,
                          @PathVariable("x") String x,
                          @PathVariable("y") String y) throws IOException
  {
    File imageFile = buildFileByElements(new File(storageDir),server,"DIM"+dimension,level,x+","+y+"-opaque.png");
    if(!imageFile.exists())
    {
      File originalImageFile = buildFileByElements(new File(storageDir),server,"DIM"+dimension,level,x+","+y+".png");
      
      if(!originalImageFile.exists())
      {
        LOG.error("Could not find file: "+originalImageFile.getAbsolutePath());
        throw new IllegalArgumentException("Could not find file:" +originalImageFile.getName());
      }
      
      ImageIO.write(PNGMerge.blackBackgroundedImage(ImageIO.read(originalImageFile)), "png", imageFile);
    }
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    IOUtils.copy(new FileInputStream(imageFile), baos);
    return baos.toByteArray();
  }
  
  private File buildFileByElements(File parent,String... elements)
  {
    for(String element : elements)
      parent = new File(parent,element);
    return parent;
  }
  
  @RequestMapping(value="/maps", method = RequestMethod.GET)
  public List listMaps()
  {
    List ret = new ArrayList();
    File mainDir = new File(storageDir);
    if(!mainDir.exists()) return ret;
    for(File file : mainDir.listFiles())
    {
      if(file.isDirectory())
      {
        ret.add(file.getName());
      }
    }
    return ret;
  }

  @RequestMapping(value="maps/{server}/data", method = RequestMethod.GET)
  public Map mapData(@PathVariable("server") String server) throws IOException
  {
    Map<String,Object> ret = new HashMap<String,Object>();

    File serverDir = new File(storageDir,server);
    if(!serverDir.exists() || !serverDir.isDirectory())
    {
      ret.put("message", "Server "+server+" does not exist.");
      ret.put("error",true);
      return ret;
    }
    
    
    ObjectMapper om = new ObjectMapper();
    for(File dimension : serverDir.listFiles())
    {
      if(!dimension.isDirectory()) continue;
      if(dimension.getName().equals("waypoints"))
      {
        List<Map> waypoints = new ArrayList<>();
        ret.put("waypoints", waypoints);
        for(File waypoint : dimension.listFiles())
        {
          waypoints.add(om.readValue(waypoint, new TypeReference<Map>(){}));
        }
      } else {
        Map<String,Object> thisDimension = new HashMap<>();
        Map<String,Object> dimensions = (Map)ret.computeIfAbsent("dimensions", k->new HashMap<String,Object>());
        dimensions.put(dimension.getName(),thisDimension);
        thisDimension.put("key",dimension.getName().replaceAll("DIM", ""));
        for(File layer : dimension.listFiles())
        {
          if(!layer.isDirectory()) continue;
          Map<String,Object> layers = (Map)thisDimension.computeIfAbsent("layers", k->new HashMap<String,Object>());
          List<String> images = new ArrayList<String>();
          ((Map)layers.computeIfAbsent(layer.getName(), k->new HashMap<String,Object>())).put("images", images);
          for(File image : layer.listFiles())
          {
            if(!image.getName().endsWith(".png")) continue;
            images.add(image.getName());
          }
        }
      }
    }
    
    return ret;
  }
  
  @RequestMapping(value="maps/{server}", produces="application/zip")
  public byte[] downloadMap(@PathVariable("server") String server, HttpServletResponse response) throws IOException
  {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    BufferedOutputStream bufstream = new BufferedOutputStream(baos);
    ZipOutputStream zip = new ZipOutputStream(bufstream);
    
    addDirectoryContents(null,new File(storageDir,server),zip);

    zip.finish();
    zip.flush();
    IOUtils.closeQuietly(zip);
    IOUtils.closeQuietly(bufstream);
    IOUtils.closeQuietly(baos);
    
    response.setStatus(HttpServletResponse.SC_OK);
    response.addHeader("Content-Disposition", "attachment; filename=\""+server+".zip\"");
    return baos.toByteArray();
  }
  
  private void addDirectoryContents(String fullDirPath, File directory, ZipOutputStream zip) throws IOException
  {
    if(fullDirPath==null) fullDirPath="";
    for(File file : directory.listFiles())
    {
      if(file.isDirectory())
      {
        if(fullDirPath.length()>0)
        {
          addDirectoryContents(fullDirPath+"/"+file.getName(),file,zip);
        } else {
          addDirectoryContents(file.getName(),file,zip);
        }
      } else {
        String entryName="";
        if(fullDirPath.length()==0)
        {
          entryName=file.getName();
        } else {
          entryName=fullDirPath+"/"+file.getName();
        }
          
        ZipEntry entry = new ZipEntry(entryName);
        zip.putNextEntry(entry);
        
        FileInputStream in = new FileInputStream(file);
        IOUtils.copy(in, zip);
        in.close();
        zip.closeEntry();
      }
    }
  }
  
  @RequestMapping(value="/maps/{server}", method = RequestMethod.POST)
  public Map uploadMap(@PathVariable("server") String server,
                       @RequestParam("file") MultipartFile file)
  {
    Map ret = new HashMap();
    ret.put("server",server);
    Map<String,File> tempFiles = null;
    try
    {
      tempFiles = this.extractFilesFromZipUpload(file);
    }catch (IOException ex)
    {
      ret.put("error", "There was an error parsing the zip file. -- "+ex.getMessage());
      return ret;
    }
    
    File serverDir = new File(storageDir,server);
    if(!serverDir.exists())
    {
      serverDir.mkdirs();
    }
    Map<String,File> existingFiles = loadServerFiles(null,serverDir);
    List<String> messages = new ArrayList<>();
    ret.put("messages", messages);
    for(String name : tempFiles.keySet()) {LOG.debug("NEW--"+name);}
    for(String name : existingFiles.keySet()) {LOG.debug("EXISTING--"+name);}
    for(Map.Entry<String,File> newFile : tempFiles.entrySet())
    {
      if(existingFiles.containsKey(newFile.getKey()))
      {
        mergeFiles(newFile.getValue(),existingFiles.get(newFile.getKey()));
      } else {
        File newFileHandle = buildFile(serverDir,newFile.getKey().split("/"));
        try
        {
          FileUtils.copyFile(newFile.getValue(),newFileHandle,true);
        }
        catch(IOException ex)
        {
          messages.add("ERROR!  Could not copy "+newFile.getValue().getAbsolutePath()+" to "+newFileHandle.getAbsolutePath()+" -- "+ex.getMessage());
          ex.printStackTrace();
        }
        LOG.debug("Last modified date on - "+newFileHandle.getName()+" set to "+newFileHandle.lastModified());
      }
    }
    
    ret.put("zipFiles", tempFiles.keySet());
    ret.put("serverFiles", existingFiles.keySet());
    return ret;
  }
  
  private Map<String,File> extractFilesFromZipUpload(MultipartFile file) throws IOException
  {
    Map<String,File> ret = new HashMap<>();
    String baseDirName=null;
    //First catalog it, to see what we've got.
    File temp = File.createTempFile("map", null);
    temp.deleteOnExit();
    file.transferTo(temp);
    ZipInputStream zipin = new ZipInputStream(new FileInputStream(temp));
    ZipEntry entry = zipin.getNextEntry();
    byte[] buf = new byte[1024];
    do
    {
        FileOutputStream out = null;
        String filename = entry.getName();
        if(isJunkEntry(entry.getName()) || entry.isDirectory())
        {
          continue;
        }
        try
        {
          ret.put(filename,File.createTempFile(filename, null));
          LOG.debug("Incoming timestamp on zip - "+filename+" - "+entry.getTime());
          ret.get(filename).deleteOnExit();
          out = new FileOutputStream(ret.get(filename));
          IOUtils.copy(zipin, out);
          ret.get(filename).setLastModified(entry.getTime());
        }
        finally
        {
          // we must always close the output file
          if(out!=null) out.close();
        }
    }while((entry = zipin.getNextEntry()) != null);
    baseDirName = tryToGuessBaseDir(ret.keySet());
    if(baseDirName!=null)
    {
      for(String key : new HashSet<String>(ret.keySet()))
      {
        ret.put(key.replace(baseDirName+"/",""), ret.remove(key));
      }
    }
    return ret;
  }
  
  private File buildFile(File base, String[] dirNames)
  {
    File ret = base;
    for(int loop=0; loop<dirNames.length-1; loop++)
    {
      ret = new File(ret,dirNames[loop]);
      if(!ret.exists())
        ret.mkdirs();
    }
    return new File(ret,dirNames[dirNames.length-1]);
  }
  
  private void mergeFiles(File newFile, File oldFile)
  {
    LOG.debug("Trying to merge "+newFile);
    if(newFile.getName().contains(".png"))
    {
      LOG.debug(newFile+" is a PNG!");
      try
      {
        long updateTime=newFile.lastModified()>oldFile.lastModified()?newFile.lastModified():oldFile.lastModified();
        ImageIO.write(PNGMerge.mergeImages(newFile, oldFile),"png",oldFile);
        oldFile.setLastModified(updateTime);
      }catch(IOException ex)
      {
        LOG.debug("Couldn't merge "+newFile.getName()+ " and "+oldFile.getName());
        ex.printStackTrace();
      }
    } else if(newFile.getName().contains(".json"))
    {
      LOG.debug(newFile+" is a waypoint.");
    }
  }
  
  private Map<String,File> loadServerFiles(String base, File directory)
  {
    if(base==null || base.length()==0)
      base="";
    else 
      base=base+"/";
    Map<String,File> ret = new HashMap<>();
    
    if(!directory.exists())
    {
      LOG.warn("Warning!  "+directory.getAbsolutePath().toString()+" does not exist!");
      return ret;
    }
    if(!directory.isDirectory())
    {
      ret.put(directory.getName(), directory);
      LOG.warn("Warning!  "+directory.getAbsolutePath().toString()+" is a file, not a directory!");
      return ret;
    }
    
    for(File file : directory.listFiles())
    {
      if(isJunkEntry(file.getName())) continue;
      if(file.isDirectory())
      {
        ret.putAll(loadServerFiles(base+file.getName(),file));
      } else {
        ret.put(base+file.getName(),file);
      }
    }
        
    return ret;
  }
  
  private String tryToGuessBaseDir(Set<String> filenames)
  {
    final String baseDirGuess = filenames.iterator().next().split("/")[0];
    if(filenames.stream()
                .allMatch(name -> name.split("/")[0].equals(baseDirGuess)))
      return baseDirGuess;
    return null;
  }
  
  private boolean isJunkEntry(String path)
  {
    if(path==null) return true;
    if(path.startsWith(".")) return true;
    if(path.startsWith("__MACOSX")) return true;
    return false;
  }
}
