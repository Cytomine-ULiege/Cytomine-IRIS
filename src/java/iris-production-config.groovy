
// THIS IS THE PRODUCTION EXTERNAL CYTOMINE IRIS CONFIGURATION FILE
// use ConfigSlurper Syntax to configure the settings
println "loading production config..."

grails.logging.jul.usebridge = false

grails.host = "leonardo.medunigraz.at"
grails.port = ""
grails.protocol = "https"
grails.serverURL = grails.protocol + "://" + grails.host + ((grails.port=="")?"":":" + grails.port)
grails.cytomine.apps.iris.host = grails.serverURL + "/iris"

// set some synchronization settings
grails.cytomine.apps.iris.sync.clientIdentifier = "IRIS_GRAZ_PROD"
grails.cytomine.apps.iris.sync.irisHost = grails.host

// Job configuration
// disable the jobs using the "disabled"=true flag
PingCytomineHostJob.disabled = true
SynchronizeUserProgressJob.disabled = false

// MAIL SERVER CONFIGURATION
grails.mail.default.from=("cytomine-iris@"+grails.host)
grails.mail.host = "relay.medunigraz.at"
grails.mail.port = 25
grails.mail.props = [
        "mail.smtp.from":("cytomine-iris@"+grails.host),
        "mail.smtp.timeout": 15000,
        "mail.smtp.connectiontimeout": 15000
		]

println "loaded production config."