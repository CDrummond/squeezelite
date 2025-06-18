/*
 *  Squeezelite Android
 *
 *  (c) Craig Drummond 2025 <craig.p.drummond@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.lyrion.squeezelite;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

public class JsonRpc {
    private final RequestQueue requestQueue;
    private final ServerDiscovery.Server server;
    private final String mac;

    private static class Request extends JsonObjectRequest {
        public Request(String url, @Nullable JSONObject request, Response.Listener<JSONObject> responseListener) {
            super(Request.Method.POST, url, request, responseListener, null);
        }
    }

    public JsonRpc(Context context, ServerDiscovery.Server server, String mac) {
        requestQueue = Volley.newRequestQueue(context);
        this.server = server;
        this.mac = mac;
    }

    public void sendMessage(String[] command) {
        try {
            JSONObject request = new JSONObject();
            JSONArray params = new JSONArray();
            JSONArray cmd = new JSONArray();
            params.put(0, mac);
            for (String c : command) {
                cmd.put(cmd.length(), c);
            }
            params.put(1, cmd);
            request.put("id", 1);
            request.put("method", "slim.request");
            request.put("params", params);

            Utils.info("MSG:" + request);
            requestQueue.add(new Request("http://" + server.ip + ":" + server.port + "/jsonrpc.js", request, null));
        } catch (Exception e) {
            Utils.error("Failed to send control message", e);
        }
    }
}