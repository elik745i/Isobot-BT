/*
 * This is I-Sobot Arduino Nano + BT-05 module sketch.
//-------------------buttoncodes-------------------------
#define forward      898819
#define backward     964611
#define sideright    703491
#define sideleft     637699
#define fleft        1030403
#define fright       571907
#define bleft        834819
#define bright       900611
#define fclockwise   966403
#define fcounter     1032195
#define bclockwise   573699
#define bcounter     639491
#define headleft     907015
#define headright    775948
#define leanforward  841478
#define leanback     1038081
#define lpunch       922368
#define r12          661248
#define lchop        858368
#define sidechopl    663040
#define combopunch   597248
#define rpunch       988160
#define rchop        924160
#define l12          792576
#define sidechopr    728832
#define lbackhand    529664
#define doublechop   989952
#define doublebackhand 925952
#define slapping     860160
#define rbackhand    595456
#define upperchop    531456
#define roundhousel  991744
#define roundhouser  533248
#define forwardkickl 599040
#define forwardkickr 664832
#define sidekickl    730624
#define roundhouselr 666624
#define forwardkicklr 732416
#define combokick    798208
#define sidekickr    796416
#define backkickl    927744
#define backkickr    993536
#define highkickl    864000
#define highkickr    995328
#define splits1      536832
#define guardl       602624
#define guardr       668416
#define doubleguard1 734208
#define doubleguard2 800000
#define dodgel       865792
#define dodger       931584
#define duck         604160
#define swayback     669952
#define upblock      735744
#define splits2      801536
#define comboblock   867328
#define zero         1034752
#define homeposition 775424
#define soundoff     840451
#define affirm       540416
#define disagree     803328
#define goodmorning  934912
#define greet1       1000704
#define greet2       608000
#define greet3       739328
#define greet4       805120
#define bye1         870912
#define bye2         936704
#define bye3         1002496
#define bye4         544000
#define bye5         542208
#define respect      869120
#define thanks1      609792
#define thanks2      675584
#define love1        872704
#define love2        938496
#define love3        1004288
#define standupfront 933120
#define standupback  998912
#define excited1     743168
#define excited2     874496
#define excited3     940288
#define excited4     618752
#define party        677376
#define amazed       750336
#define regret1      547584
#define regret2      744960
#define regret3      810752
#define worry        679168
#define pain1        1007872
#define pain2        615168
#define beg1         942080
#define beg2         880128
#define merry        552960
#define hilarious    1013504
#define hidenseek    613376
#define youlike      682752
#define mystery5     748544
#define tipsy        814336
#define tickleme     686080
#define tiredfeet    751872
#define needabreak   817664
#define wave1        883456
#define wave2        949248
#define applause     947712
#define mystery6     945920
#define toosexy      1015040
#define clink        556544
#define relax        753664
#define soccer1      885248
#define soccer2      600832
#define soccer3      535040
#define lift         819456
#define countonme    951040
#define articulation 1016832
#define showoff1     558336
#define showoff2     624128
#define showoff3     689920
#define showoff4     821248
#define cominthrough 887040
#define catch        1006080
#define pose1        771840
#define pose2        903168
#define pose3        968960
#define mystery1     684544
#define mystery2     816128
#define mystery3     881920
#define mystery4     549376
#define forwardsomersault  952832
#define headstandexercises 1018624
#define exercises    560128
#define airdrum      625920
#define airguitar    691712
#define randomperformance1 954624
#define randomanimal   627712
#define tropicaldance  825088
#define giantrobot     956416
#define western        1022208
#define randomperformance2  629504
 */
 
#include <Isobot.h>
#include <SoftwareSerial.h>

Isobot bot(5);      //IR transmitter hooked to D5
unsigned long time1, time2;
String data0;
String data1;

unsigned long Code[] = {
  forward,                                  // 0
  backward,                                 // 1
  sideright,                                // 2
  sideleft,                                 // 3
  fleft,                                    // 4
  fright,                                   // 5
  bleft, 
  bright, 
  fclockwise, 
  fcounter, 
  bclockwise,                               // 10
  bcounter, 
  headleft, 
  headright,  
  leanforward,
  leanback, 
  lpunch, 
  r12,  
  lchop, 
  sidechopl, 
  combopunch,                               // 20
  rpunch, 
  rchop, 
  l12, 
  sidechopr, 
  lbackhand, 
  doublechop,  
  doublebackhand, 
  slapping, 
  rbackhand, 
  upperchop,                                // 30
  roundhousel,
  roundhouser, 
  forwardkickl, 
  forwardkickr, 
  sidekickl, 
  roundhouselr, 
  forwardkicklr, 
  combokick, 
  sidekickr, 
  backkickl,                               // 40
  backkickr, 
  highkickl, 
  highkickr, 
  splits1, 
  guardl, 
  guardr, 
  doubleguard1,
  doubleguard2, 
  dodgel, 
  dodger,                                 // 50
  duck, 
  swayback, 
  upblock, 
  splits2, 
  comboblock, 
  zero,  
  homeposition, 
  soundoff, 
  affirm,  
  disagree,                               // 60
  goodmorning, 
  greet1, 
  greet2, 
  greet3, 
  greet4,
  bye1, 
  bye2, 
  bye3, 
  bye4, 
  bye5,                                  // 70
  respect, 
  thanks1, 
  thanks2, 
  love1, 
  love2, 
  love3, 
  standupfront, 
  standupback, 
  excited1, 
  excited2,                             // 80
  excited3, 
  excited4, 
  party, 
  amazed, 
  regret1, 
  regret2, 
  regret3, 
  worry, 
  pain1, 
  pain2,                                // 90
  beg1, 
  beg2,  
  merry,  
  hilarious, 
  hidenseek, 
  youlike, 
  mystery5, 
  tipsy, 
  tickleme, 
  tiredfeet,                             // 100
  needabreak, 
  wave1, 
  wave2, 
  applause, 
  mystery6, 
  toosexy, 
  clink, 
  relax,
  soccer1, 
  soccer2,                              // 110
  soccer3, 
  lift, 
  countonme, 
  articulation, 
  showoff1, 
  showoff2, 
  showoff3, 
  showoff4, 
  cominthrough, 
  catch,                                // 120
  randomperformance2, 
  western, 
  giantrobot, 
  tropicaldance, 
  randomanimal, 
  randomperformance1, 
  airguitar, 
  airdrum, 
  exercises, 
  headstandexercises,                   //  130
  forwardsomersault, 
  mystery4, 
  mystery3, 
  mystery2, 
  mystery1, 
  pose3, 
  pose2, 
  pose1 };                              // 138

SoftwareSerial mySerial(3, 4); // указываем пины rx и tx соответственно

void setup() {
  // put your setup code here, to run once:
  pinMode(3,INPUT);
  pinMode(4,OUTPUT);
  Serial.begin(9600);
  mySerial.begin(9600);
  Serial.println(F("I-Sobot serial control v1.0. Please type command the push enter"));

}

void loop() {
serialCommand();
}

void serialCommand(){
  //--------------------Serial Command-----------------    

 if (Serial.available()> 0 || mySerial.available())
 {
  if(Serial.available()){
   data1 = Serial.readString();
   byte i = atoi(data1.c_str());

  if(i <= 11){
    for(byte k = 0; k < 5; k++){
        Serial.println(Code[i]);
        bot.buttonwrite(Code[i],3);         
    }
  }else{
     Serial.println(Code[i]);
     bot.buttonwrite(Code[i],3);        
    }
  }
  if(mySerial.available()){
    data0 = mySerial.readString();
    byte ir = atoi(data0.c_str()); 
    if(ir <= 11){
      for(byte k = 0; k < 5; k++){
        Serial.println(Code[ir]);
        bot.buttonwrite(Code[ir],3);         
    }
  }else{
        Serial.println(Code[ir]);
        bot.buttonwrite(Code[ir],3);          
    }
  }
        
 }  
}
