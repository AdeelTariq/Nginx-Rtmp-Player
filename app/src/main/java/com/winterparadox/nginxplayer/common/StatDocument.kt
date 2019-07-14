package com.winterparadox.nginxplayer.common

import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

@Root(strict = false, name = "rtmp")
class Rtmp {
    @field:Element(name = "server")
    var server: Server? = null

    override fun toString(): String {
        return "ClassPojo [server = $server]"
    }
}

class Server {
    @field:Element(name = "application")
    var application: Application? = null

    override fun toString(): String {
        return "ClassPojo [application = $application]"
    }
}

class Application {
    @field:Element(name = "name")
    var name: String? = null

    @field:Element(name = "live")
    var live: Live? = null

    override fun toString(): String {
        return "ClassPojo [name = $name, live = $live]"
    }
}

class Live {
    @field:ElementList(name = "stream", inline = true, required = false)
    var streams: List<Stream>? = null

    override fun toString(): String {
        return "ClassPojo [stream = $streams]"
    }
}