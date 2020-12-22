#include <Isobot.h>

Isobot bot(7);
unsigned long time1, time2;

void setup(){
 time1=time2=millis();
 pinMode(13, OUTPUT);
}

void loop(){
 if(millis()>(5000+time1)){
   digitalWrite(13, HIGH);
   bot.buttonwrite(homeposition,3);
   
   time1=millis();
   digitalWrite(13,LOW);
 }
 
 if(millis()>(20000+time2)){
     digitalWrite(13,HIGH);
     bot.buttonwrite(rpunch,3);
     
     time2=time1=millis();
     digitalWrite(13,LOW);
 }
}
