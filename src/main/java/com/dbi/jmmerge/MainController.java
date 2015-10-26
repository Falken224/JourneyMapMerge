/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.dbi.jmmerge;

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
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
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
public class MainController {

  @Value("#{systemProperties['jmmerge.data.dir'] ?: '/var/data/jmmerge'}")
  private String storageDir;
  
  @ExceptionHandler
  public ResponseEntity<Map> handleException(Exception ex)
  {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map msgs = new HashMap();
    ResponseEntity<Map> ret = new ResponseEntity<Map>(msgs, headers, HttpStatus.INTERNAL_SERVER_ERROR);
    msgs.put("message", "An error occurred . . . contact your administrator for details.");
    msgs.put("error",ex.getMessage());
    return ret;
  }
  
  @RequestMapping(value="/maps", method = RequestMethod.GET)
  public List listMaps()
  {
    List ret = new ArrayList();
    for(File file : new File(storageDir).listFiles())
    {
      if(file.isDirectory())
      {
        ret.add(file.getName());
      }
    }
    return ret;
  }
  
  @RequestMapping(value="map/{server}", produces="application/zip")
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
  
  @RequestMapping(value="/maps", method = RequestMethod.POST)
  public Map uploadMap(@RequestParam("server") String server,
                       @RequestParam("file") MultipartFile file)
  {
    Map ret = new HashMap();
    ret.put("server",server);
    Map<String,File> tempFiles = new HashMap<>();
    try
    {
      String baseDirName=null;
      //First catalog it, to see what we've got.
      File temp = File.createTempFile("map", null);
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
            tempFiles.put(filename,File.createTempFile(filename, null));
            tempFiles.get(filename).deleteOnExit();
            out = new FileOutputStream(tempFiles.get(filename));
            int len = 0;
            while ((len = zipin.read(buf)) > 0)
            {
                out.write(buf, 0, len);
            }
          }
          finally
          {
            // we must always close the output file
            if(out!=null) out.close();
          }
      }while((entry = zipin.getNextEntry()) != null);
      baseDirName = tryToGuessBaseDir(tempFiles.keySet());
      if(baseDirName!=null)
      {
        for(String key : new HashSet<String>(tempFiles.keySet()))
        {
          tempFiles.put(key.replace(baseDirName+"/",""), tempFiles.remove(key));
        }
      }
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
    for(String name : tempFiles.keySet()) {System.out.println("NEW--"+name);}
    for(String name : existingFiles.keySet()) {System.out.println("EXISTING--"+name);}
    for(Map.Entry<String,File> newFile : tempFiles.entrySet())
    {
      if(existingFiles.containsKey(newFile.getKey()))
      {
        mergeFiles(newFile.getValue(),existingFiles.get(newFile.getKey()));
      } else {
        File newFileHandle = buildFile(serverDir,newFile.getKey().split("/"));
        try
        {
          FileUtils.copyFile(newFile.getValue(),newFileHandle);
        }
        catch(IOException ex)
        {
          messages.add("ERROR!  Could not copy "+newFile.getValue().getAbsolutePath()+" to "+newFileHandle.getAbsolutePath()+" -- "+ex.getMessage());
          ex.printStackTrace();
        }
      }
    }
    
    ret.put("zipFiles", tempFiles.keySet());
    ret.put("serverFiles", existingFiles.keySet());
    return ret;
  }
  
  private File buildFile(File base, String[] dirNames)
  {
    System.out.println("building "+base+" + ");
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
    System.out.println("Trying to merge "+newFile);
    if(newFile.getName().contains(".png"))
    {
      System.out.println(newFile+" is a PNG!");
      try
      {
        ImageIO.write(PNGMerge.mergeImages(newFile, oldFile),"png",oldFile);
      }catch(IOException ex)
      {
        System.out.println("Couldn't merge "+newFile.getName()+ " and "+oldFile.getName());
        ex.printStackTrace();
      }
    } else if(newFile.getName().contains(".json"))
    {
      System.out.println(newFile+" is a waypoint.");
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
      System.out.println("Warning!  "+directory.getAbsolutePath().toString()+" does not exist!");
      return ret;
    }
    if(!directory.isDirectory())
    {
      ret.put(directory.getName(), directory);
      System.out.println("Warning!  "+directory.getAbsolutePath().toString()+" is a file, not a directory!");
      return ret;
    }
    
    for(File file : directory.listFiles())
    {
      if(file.getName().startsWith(".") || isJunkEntry(file.getName())) continue;
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
    String baseDirGuess = null;
    int matchCount = 0;
    for(String filename : filenames)
    {
      if(baseDirGuess==null)
      {
        baseDirGuess = filename.split("/")[0];
        matchCount++;
      } else {
        if(filename.split("/")[0].equals(baseDirGuess))
        {
          matchCount++;
        }
      }
    }
    if(matchCount==filenames.size())
    {
      return baseDirGuess;
    } else {
      System.out.println("Only "+matchCount+" out of "+filenames.size()+" started with "+baseDirGuess+".  Not guessing that.");
    }
    return null;
  }
  
  private boolean isJunkEntry(String path)
  {
    if(path==null) return true;
    if(path.startsWith("__MACOSX")) return true;
    return false;
  }
}
