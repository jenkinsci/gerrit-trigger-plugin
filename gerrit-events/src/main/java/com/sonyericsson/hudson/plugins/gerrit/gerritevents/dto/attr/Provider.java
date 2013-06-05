/*
 *  The MIT License
 *
 *  Copyright 2013 rinrinne. All rights reserved.
 *  Copyright 2013 Sony Mobile Communications AB. All rights reserved.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr;

import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritJsonEventFactory.getString;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.NAME;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.HOST;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.PORT;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.PROTO;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.URL;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.VERSION;

import net.sf.json.JSONObject;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritJsonDTO;

/**
 * Represents a Gerrit JSON Provider DTO.
 * An Provider that is related to an event or attribute.
 *
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
public class Provider implements GerritJsonDTO {

    /**
     * The name
     */
    private String name;
    /**
     * The host where serve this event.
     */
    private String host;
    /**
     * The port where listen connection.
     */
    private String port;
    /**
     * The proto which through this event.
     */
    private String proto;
    /**
     * The url where serve gerrit Web UI.
     */
    private String url;
    /**
     * The version.
     */
    private String version;
    /**
     * Default constructor.
     */
    public Provider() {
    }

    /**
     * Constructor that fills with data directly.
     *
     * @param json the JSON Object with data.
     * @see #fromJson(net.sf.json.JSONObject)
     */
    public Provider(JSONObject json) {
        fromJson(json);
    }

    /**
     * For easier testing.
     * @param name the host for SSH interface.
     * @param host the host for SSH interface.
     * @param port the port for SSH interface.
     * @param proto the port for SSH interface.
     * @param url the frontend URL for gerrit WebUI.
     * @param version the gerrit version.
     */
    public Provider(String name, String host, String port, String proto, String url, String version) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.proto = proto;
        this.url = url;
        this.version = version;
    }

    @Override
    public void fromJson(JSONObject json) {
        name = getString(json, NAME);
        host = getString(json, HOST);
        port = getString(json, PORT);
        proto = getString(json, PROTO);
        url = getString(json, URL);
        version = getString(json, VERSION);
    }

    /**
     * Get name.
     *
     * @return the name.
     */
    public String getName() {
        return name;
    }

    /**
     * Set name.
     *
     * @param name the name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get host where serve this event.
     *
     * @return the host.
     */
    public String getHost() {
        return host;
    }

    /**
     * Set host where server this event.
     *
     * @param host the host.
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Get port where listen connection.
     *
     * @return the port.
     */
    public String getPort() {
        return port;
    }

    /**
     * Set port where listen connection.
     *
     * @param port the port.
     */
    public void setPort(String port) {
        this.port = port;
    }

    /**
     * Get proto which through this event.
     *
     * @return the proto.
     */
    public String getProto() {
        return proto;
    }

    /**
     * Set proto which through this event.
     *
     * @param proto the proto.
     */
    public void setProto(String proto) {
        this.proto = proto;
    }

    /**
     * Get url where serve gerrit Web UI.
     *
     * @return the url
     */
    public String getUrl() {
        String frontUrl = this.url;
        if (frontUrl != null && !frontUrl.isEmpty() && !frontUrl.endsWith("/")) {
            frontUrl += '/';
        }
        return frontUrl;
    }

    /**
     * Set url where serve gerrit Web UI.
     *
     * @param url the url.
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Get version.
     *
     * @return the version.
     */
    public String getVersion() {
        return version;
    }

    /**
     * Set version.
     *
     * @param version the version.
     */
    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "Provider: " + getName() + " " + getHost() + " " + getPort() + " " + getProto()
                + " " + getUrl() + " " + getVersion();
    }

    @Override
    public int hashCode() {
        //CS IGNORE MagicNumber FOR NEXT 9 LINES. REASON: Autogenerated Code.
        //CS IGNORE AvoidInlineConditionals FOR NEXT 9 LINES. REASON: Autogenerated Code.
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((host == null) ? 0 : host.hashCode());
        result = prime * result + ((port == null) ? 0 : port.hashCode());
        result = prime * result + ((proto == null) ? 0 : proto.hashCode());
        result = prime * result + ((url == null) ? 0 : url.hashCode());
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        //CS IGNORE NeedBraces FOR NEXT 38 LINES. REASON: Autogenerated Code.
        //CS IGNORE NoWhitespaceAfter FOR NEXT 38 LINES. REASON: Autogenerated Code.
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Provider other = (Provider) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (host == null) {
            if (other.host != null)
                return false;
        } else if (!host.equals(other.host))
            return false;
        if (port == null) {
            if (other.port != null)
                return false;
        } else if (!port.equals(other.port))
            return false;
        if (proto == null) {
            if (other.proto != null)
                return false;
        } else if (!proto.equals(other.proto))
            return false;
        if (url == null) {
            if (other.url != null)
                return false;
        } else if (!url.equals(other.url))
            return false;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
        return true;
    }
}
