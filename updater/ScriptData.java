package updater;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by Spencer on 11/3/2016.
 */
public class ScriptData {
    public String id, name, version, status, packName;
    public boolean selected = false;

    public ScriptData(JSONObject obj) {
        try {
            id = obj.getString("id");
            name = obj.getString("name");
            version = obj.getString("version");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
