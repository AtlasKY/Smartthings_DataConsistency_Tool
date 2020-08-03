
definition(
	name: "Smarter Security Camera",
	namespace: "zzarbi",
	author: "Nicolas Cerveaux",
	description: "Let you choose a preset for different mode\r\n* Based on Smart Security Camera of BLebson (https://github.com/blebson/Smart-Security-Camera/blob/master/smartapps/blebson/smart-security-camera.src/smart-security-camera.groovy)",
	category: "Safety & Security",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Partner/photo-burst-when.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Partner/photo-burst-when@2x.png",
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Partner/photo-burst-when@2x.png")



preferences {
	section("When there's movement...") {
		input "motion1", "capability.motionSensor", title: "Where?", multiple: true
	}
	section("Turn on a light...") {
		input "switch1", "capability.switch", multiple: true
	}
}
	
def installed()
{
	subscribe(motion1, "motion.active", motionActiveHandler)
}
	
def updated()
{
	unsubscribe()
	subscribe(motion1, "motion.active", motionActiveHandler)
}
	
def motionActiveHandler(evt) {
	switch1.on()
}

