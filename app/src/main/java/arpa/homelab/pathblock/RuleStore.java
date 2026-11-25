package arpa.homelab.pathblock;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class RuleStore {
    private static final String FILE = "pathblock";
    private static final String KEY = "rules";
    private static final String GUARD_CONSENT = "guard_consent";

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean hasGuardConsent(Context context) {
        return preferences(context).getBoolean(GUARD_CONSENT, false);
    }

    static void setGuardConsent(Context context, boolean consent) {
        preferences(context).edit().putBoolean(GUARD_CONSENT, consent).apply();
    }

    static List<Rule> read(Context context) {
        List<Rule> rules = new ArrayList<>();
        try {
            JSONArray items = new JSONArray(preferences(context).getString(KEY, "[]"));
            for (int index = 0; index < items.length(); index++) {
                JSONObject item = items.getJSONObject(index);
                Rule.Address address = new Rule.Address(item.getString("host"),
                        item.getInt("port"), item.getString("path"));
                if (rules.stream().noneMatch(rule -> rule.address.equals(address))) {
                    rules.add(new Rule(address, item.optLong("pausedUntil", 0)));
                }
            }
        } catch (JSONException ignored) {
            rules.clear();
        }
        return rules;
    }

    static void write(Context context, List<Rule> rules) {
        JSONArray items = new JSONArray();
        for (Rule rule : rules) {
            try {
                items.put(new JSONObject().put("host", rule.address.host())
                        .put("port", rule.address.port()).put("path", rule.address.path())
                        .put("pausedUntil", rule.pausedUntil));
            } catch (JSONException error) {
                throw new IllegalStateException(error);
            }
        }
        preferences(context).edit().putString(KEY, items.toString()).apply();
    }
}
