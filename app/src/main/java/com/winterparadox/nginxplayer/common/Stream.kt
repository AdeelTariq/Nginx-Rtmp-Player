package com.winterparadox.nginxplayer.common

import org.simpleframework.xml.Element
import org.simpleframework.xml.Root

@Root(name = "stream", strict = false)
class Stream(@field:Element(name = "name") var name: String? = "") {

    override fun toString(): String {
        return "ClassPojo [name = $name]"
    }

    override fun equals(other: Any?): Boolean {
        if (other is Stream) {
            return name == other.name
        }
        return super.equals(other)
    }
}