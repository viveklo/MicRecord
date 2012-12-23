import java.text.*;
import java.util.*;
import java.io.*;

import java.awt.*;
import java.awt.event.*;
import javax.sound.sampled.*;
import javax.swing.*;

import java.util.concurrent.atomic.AtomicBoolean;

public class MicRecordui extends JFrame{

  ByteArrayOutputStream byteArrayOutputStream;
  AudioFormat audioFormat;
  TargetDataLine targetDataLine;
  AudioInputStream audioInputStream;
  SourceDataLine sourceDataLine;
  File audioFile = null;
  String username = "unknown";
  String hostname = "unknown";
  String hostaddress = "unknown";
  String currentdate = "unknown";
  String currenthour = "unknown";

  String micmode = "automatic";
  int micrecordduration = 15000;
  String micftpmode = "ftpy";
  
  AtomicBoolean startallthread = null;
  AtomicBoolean startcaptureflag = null;

  String move = null;
  String del = null;
  SimpleFTP ftp = null;

  Writer miclogfile = null;

  boolean green_button_set = true;

  ActionListener toggleListener;
  ActionListener changeListener;

  public static void main(String args[]){
    try {
    
         String mode = "automatic";
         int recordduration = 15000;
         String ftpmode = "ftpy";
         String helpstring = "Help - MicRecord mode[manual automatic] Recordduration in ms (between 15 sec to 3 min) ftp[ftpy ftpn]";

         //get the args
          if (args.length < 3){
            System.out.println(helpstring);
            System.exit(0);
          }
          mode = args[0];
          if (!(mode.equals("manual")) && !(mode.equals("automatic"))){
              System.out.println("Mode Incorrect");
              System.out.println("Mode Incorrect "+helpstring);
              System.exit(0);
          }

          recordduration = Integer.parseInt(args[1]);
          if ((recordduration < 15000) || (recordduration > 180000)){
            System.out.println("Recordduration Incorrect");
            System.out.println(helpstring);
            System.exit(0);
          }

          ftpmode = args[2];
          if (!(ftpmode.equals("ftpy")) && !(ftpmode.equals("ftpn"))){
              System.out.println("ftp mode Incorrect");
              System.out.println(helpstring);
              System.exit(0);
          }

         new MicRecordui(mode, recordduration, ftpmode);

    } catch (Exception e) {
      System.out.println(e);
      e.printStackTrace();
      System.exit(0);
    }//end catch

  }//end main

  public MicRecordui(String mode, int recordduration, String ftpmode){//constructor
  try{

    miclogfile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("micrecord.log"), "utf-8"));
    miclogfile.write("MicRecordui \n");
    miclogfile.flush();
    String os = System.getProperty("os.name");
    micmode = mode;
    micrecordduration = recordduration;
    micftpmode = ftpmode;

    startallthread = new AtomicBoolean(false);
    startcaptureflag = new AtomicBoolean(false); 
    
    move = "cmd /c move ";
    del = "cmd /c del ";
    if (os.equals("Linux")){
      move = "mv ";
      del = "rm ";
    }
    final ImageIcon greenbutton = new ImageIcon("img/green_button_small.gif");
    final ImageIcon redbutton = new ImageIcon("img/red_button_small.gif");
    final ImageIcon redgreybutton = new ImageIcon("img/red_grey_small.gif");
    final JLabel modedisplay = new JLabel();
    final JButton toggleBtn =
                       new JButton("<html><font color='white'>Press to Start Recording</font></html>",greenbutton);
    toggleBtn.setHorizontalTextPosition(JButton.CENTER);
    toggleBtn.setVerticalTextPosition(JButton.CENTER);
    toggleBtn.setBorder(null);
    toggleBtn.setEnabled(true);

    final JButton changeBtn = new JButton();
    if (micmode.equals("manual")){
      modedisplay.setText("Mode: Manual");
      changeBtn.setText("Change to Automatic Mode");
    }
    else{
      modedisplay.setText("Mode: Automatic");
      changeBtn.setText("Change to Manual Mode");
    }//end if micmode

    changeListener = new ActionListener(){
        public void actionPerformed(
                              ActionEvent e){
          if (micmode.equals("manual")){
            //change to automatic
            micmode = "automatic";
            modedisplay.setText("Mode: Automatic");
            changeBtn.setText("Change to Manual Mode");
            green_button_set = false;
            toggleBtn.setIcon(redgreybutton);
            toggleBtn.setText("<html><font color='white'>Recording in Progress</font></html>");
            toggleBtn.removeActionListener(toggleListener);//end addActionListener()
            startcaptureAudio();
          }//end of micmode
          else{//automatic mode
            stopcaptureAudio();
            micmode = "manual";
            modedisplay.setText("Mode: Manual");
            changeBtn.setText("Change to Automatic Mode");
            green_button_set = true;
            toggleBtn.setIcon(greenbutton);
            toggleBtn.setText("<html><font color='white'>Press to Start Recording</font></html>");
            //add action listener
            toggleBtn.addActionListener(toggleListener);//end addActionListener()
          }//endif micmode
        }//end actionPerformed
      };//end ActionListener


    toggleListener = new ActionListener(){
           public void actionPerformed(
                                 ActionEvent e){
             if(green_button_set){
               green_button_set = false;
               toggleBtn.setIcon(redbutton);
               toggleBtn.setText("<html><font color='white'>Press to Stop Recording</font></html>");
               // microphone until the Stop button is
               // clicked.
               startcaptureAudio();
             }
             else{
               green_button_set = true;
               toggleBtn.setIcon(greenbutton);
               toggleBtn.setText("<html><font color='white'>Press to Start Recording</font></html>");
               stopcaptureAudio();
             }//endif green_button_set
           }//end actionPerformed
         };//end ActionListener

    if (micmode.equals("manual")){
      changeBtn.setText("Change to Automatic Mode");
       //Register anonymous listeners
       toggleBtn.addActionListener(toggleListener);//end addActionListener()
    } //end if manual
    else{ //automatic mode
      changeBtn.setText("Change to Manual Mode");
      //start audio
      toggleBtn.setIcon(redbutton);
      toggleBtn.setText("<html><font color='white'>Recording in progress</font></html>");
      startcaptureAudio();  
    }//end ifelse manual

    changeBtn.addActionListener(changeListener);//end addActionListener()

    getContentPane().add(toggleBtn);
    getContentPane().add(modedisplay);
    getContentPane().add(changeBtn);
    getContentPane().setLayout(new FlowLayout());
    setTitle("Livedarshan Audio Record");
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setSize(250,270);
    setVisible(true);
    } catch (Exception e) {
      System.out.println(e);
      e.printStackTrace();
      System.exit(0);
    }//end catch

  }//end constructor

  //Init the audio device once
  private void initAudio(){
    try{
      //BufferedReader reader;
      int mixerchoice = 3;

      //reader = new BufferedReader(new InputStreamReader(System.in));
      //Get and display a list of
      // available mixers.
      Mixer.Info[] mixerInfo =
                      AudioSystem.getMixerInfo();
      for(int cnt = 0; cnt < mixerInfo.length; cnt++){
        miclogfile.write(cnt+1+")"+ mixerInfo[cnt].getName()+"\n");
        miclogfile.flush();
      }//end for loop

      //Get everything set up for capture
      audioFormat = getAudioFormat();

      DataLine.Info dataLineInfo =
                            new DataLine.Info(
                            TargetDataLine.class,
                            audioFormat);

      //Select one of the available
      // mixers.
      /* ***** Commenting, but might be used in future do not remove ****
      System.out.println("Select the Mixer");
      mixerchoice = Integer.parseInt(reader.readLine());
      Mixer mixer = AudioSystem.
                          getMixer(mixerInfo[mixerchoice-1]);
      //Get a TargetDataLine on the selected
      // mixer.
      targetDataLine = (TargetDataLine)
                     mixer.getLine(dataLineInfo);
       ***********/
      targetDataLine = (TargetDataLine)
               AudioSystem.getLine(dataLineInfo);
    } catch (Exception e) {
      System.out.println(e);
      e.printStackTrace();
      System.exit(0);
    }//end catch
  }//end initAudio method

//open source and start new recording
  private void opennewrecordFile(){
    try{
      audioFile = new File(getFileName()+".wav.temp");

      AudioSystem.write(
              new AudioInputStream(targetDataLine),
               AudioFileFormat.Type.WAVE,
            audioFile);
    } catch (Exception e) {
      System.out.println(e);
      e.printStackTrace();
      System.exit(0);
    }//end catch
  }//end opennewrecordFile

// rename temp file
  private void closetemprecordFile(String finalaudioFilename){
    try{
      String src = finalaudioFilename;
      String dst = finalaudioFilename.substring(0, finalaudioFilename.length()-5);
      Runtime.getRuntime().exec(move+" "+src+" "+dst);

   //   finalaudioFilename = finalaudioFilename.substring(0, finalaudioFilename.length()-5);
    //  File finalaudioFile = new File(finalaudioFilename);
    //  audioFile.renameTo(finalaudioFile);
    } catch (Exception e) {
      System.out.println(e);
      e.printStackTrace();
      System.exit(0);
    }//end catch

  }

  public String getFileName(){
        String filename = "noname";
    try {
         username = System.getProperty("user.name");
         java.net.InetAddress localmachine = java.net.InetAddress.getLocalHost();
         hostname = localmachine.getHostName();
         hostaddress = localmachine.getHostAddress();
         Date date = new Date();
         SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
         SimpleDateFormat timeFormat = new SimpleDateFormat("HH-mm-ss");
         SimpleDateFormat currenttimeFormat = new SimpleDateFormat("HH");
         String datestr = dateFormat.format(date);
         String timestr = timeFormat.format(date);
         currentdate = dateFormat.format(date);
         currenthour = currenttimeFormat.format(date);
         filename = hostname + "_" + username + "_" + hostaddress + "_"
                           + datestr + "_" + timestr;
     } catch (Exception e) {
         System.out.println(e);
         e.printStackTrace();
         System.exit(0);
       }//end catch
       return filename;
  } //end getFileName




  //This method captures audio input from a
  // microphone and saves it in a
  // ByteArrayOutputStream object.
  private void startcaptureAudio(){
    try{
      //check if threads are already running
      if (startallthread.get()==false){
        initAudio();
        Thread captureThread = new CaptureThread();
        captureThread.start();
        //wait for the capture thread to start before record thread
        Thread.sleep(10);
        Thread recordtimeThread = new RecordTimeThread();
        recordtimeThread.start();
        if (micftpmode.equals("ftpy")){ 
          //Start ftp client
          Thread ftpclientThread = new FTPClientThread();
          ftpclientThread.start();
        }//end if ftp
        //stop the Recordtime thread
      }//endif startallthread
    } catch (Exception e) {
      System.out.println(e);
      e.printStackTrace();
      System.exit(0);
    }//end catch
  }//end captureAudio method


//stocaptureAudio stops the audio time and ftp processes
  private void stopcaptureAudio(){
    try{
      startcaptureflag.set(false);
      targetDataLine.stop();
      targetDataLine.close();
      startallthread.set(false);
      String finalaudioFilename = audioFile.getName();
      closetemprecordFile(finalaudioFilename);

    } catch (Exception e) {
      System.out.println(e);
      e.printStackTrace();
      System.exit(0);
    }//end catch
  }//end captureAudio method

  //This method creates and returns an
  // AudioFormat object for a given set of format
  // parameters.  If these parameters don't work
  // well for you, try some of the other
  // allowable parameter values, which are shown
  // in comments following the declartions.
  private AudioFormat getAudioFormat(){
    float sampleRate = 8000.0F;
    //8000,11025,16000,22050,44100
    int sampleSizeInBits = 16;
    //8,16
    int channels = 1;
    //1,2
    boolean signed = true;
    //true,false
    boolean bigEndian = false;
    //true,false
    return new AudioFormat(
                      sampleRate,
                      sampleSizeInBits,
                      channels,
                      signed,
                      bigEndian);
  }//end getAudioFormat
//=============================================//

//Inner class to capture data from microphone
class CaptureThread extends Thread{
  public void run(){
    try{//Loop until stopCapture is set by
        // another thread that services the Stop
        // button.
        startallthread.set(true);
        startcaptureflag.set(true);
        miclogfile.write("Starting Audio Capturing Thread\n");
        miclogfile.flush();
        while(startallthread.get()){
          if (startcaptureflag.get()){
            targetDataLine.open(audioFormat);
            targetDataLine.start();
            opennewrecordFile();
        }//end if
      }//end while
      miclogfile.write("Stopping Capture Thread\n");
    }catch (Exception e) {
      System.out.println(e);
      e.printStackTrace();
      System.exit(0);
    }//end catch
  }//end run

}//end inner class CaptureThread
//===================================//

class RecordTimeThread extends Thread{
  public void run(){
    try{
        miclogfile.write("Starting Record Time Thread\n");
        miclogfile.flush();
        // another thread to stop after specified duration 
        while(startallthread.get()){
          // if recording has started sleep for recording duration
          if (startcaptureflag.get())
            Thread.sleep(micrecordduration);
          startcaptureflag.set(false);
          targetDataLine.stop();
          targetDataLine.close();
          //before capture starts new recoding get the recoded filename
          String finalaudioFilename = audioFile.getName();
          closetemprecordFile(finalaudioFilename);
          startcaptureflag.set(true);
        }//while
        miclogfile.write("Stopping Record Timing Thread\n");
        //Terminate the capturing of input data
        // from the microphone.
    }catch (Exception e) {
      System.out.println(e);
      e.printStackTrace();
      System.exit(0);
    }//end catch
  }//end run
}//end inner class RecordTimeThread
//===================================//

class FTPClientThread extends Thread{
  public void run(){
    try{ 
        ftp = new SimpleFTP();
        miclogfile.write("Starting FTP Thread\n");
        miclogfile.flush();
        String delimiter = "_";
        String[] temp;
        String hst, usr, dte, tme;
        boolean continueloop = true;

        String file;
        File folder = new File(".");
        File[] listOfFiles = folder.listFiles();

        //before we start check if any old temp files  are remaining
        //this is required for forced shutdown of the application in
        // previous instance
        for (int i = 0; i < listOfFiles.length; i++){
          if (listOfFiles[i].isFile()){
            file = listOfFiles[i].getName();
            if (file.endsWith(".temp")){
              //move the file
              String dst = file.substring(0, file.length()-5);
              Runtime.getRuntime().exec(move+" "+file+" "+dst);
            }//end if endsWith
          }//endif listOfFiles
        }//end for

        while(true){
          // Get the filelist from dircetory
          folder = new File(".");
          listOfFiles = folder.listFiles();

          ftp.connect("127.0.0.1", 21, "fedora", "fedora123");

          for (int i = 0; i < listOfFiles.length; i++){
            if (listOfFiles[i].isFile()){
              file = listOfFiles[i].getName();
                if (file.endsWith(".wav") || file.endsWith(".WAV")){
                  //get the hostname, username, date and hour from filename
                  // and change ftp server directory
                  ftpchangedirectory(file);
                  if (ftp.stor(new File(file))){
                    //delete the stored file
                    Runtime.getRuntime().exec(del+" "+file);
                  }
                  //change to home directory
                  ftp.cwd("/");
              } //end if file.endsWith
           }//endif listoffiles
         }//for listOfFiles
         //sleep for 10 secs
         ftp.disconnect();
         Thread.sleep(10000);
         if (startallthread.get() == false){
           //check if have looped once after startallthread was false
           if (continueloop){
             // loop one last time 
             continueloop = false;
           }
           else {
             //we have looped once after startallthread was false so break
             miclogfile.write("Stopping FTP Thread\n");
             break;
           }//end if continueloop
         } 
         else{
           continueloop = true;
         }// end if continueloop
       } //end while
     }catch (Exception e) {
      System.out.println(e);
      e.printStackTrace();
      System.exit(0);
     }//end catch
  }//run

  public void ftpchangedirectory(String fle){
    try{
    String delimiter = "_";
    String[] temp;
    String hst, usr, dte, tme;

    temp = fle.split(delimiter);
    hst = temp[0];
    usr = temp[1];
    dte = temp[3];
    tme = temp[4].substring(0, temp[4].length()-10);

    //for (int i = 0; i<temp.length; i++)
    //   System.out.println(temp[i]);

    ftp.mkd(hst);
    ftp.cwd(hst);
    ftp.mkd(usr);
    ftp.cwd(usr);
    ftp.mkd(dte);
    ftp.cwd(dte);
    ftp.mkd(tme);
    ftp.cwd(tme);
    } catch (Exception e) {
      System.out.println(e);
      e.printStackTrace();
      System.exit(0);
    }//end catch

  }//ftpchangedirectory

}//FTPClientThread
                  

}//end outer class MicRecordui.java
