package ai.flow.android.sensor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.HashMap;

public class CameraHandlerTest {

    @Test
    public void testChooseCameraIdPrefersExternal() {
        String[] ids = new String[]{"0", "1", "2"};
        HashMap<String, Integer> map = new HashMap<>();
        map.put("0", 0); // back
        map.put("1", 1); // front
        map.put("2", 2); // external

        String chosen = CameraHandler.chooseCameraId(ids, map, null);
        assertEquals("2", chosen);
    }

    @Test
    public void testChooseCameraIdOverrideSpecific() {
        String[] ids = new String[]{"0", "1"};
        HashMap<String, Integer> map = new HashMap<>();
        map.put("0", 0);
        map.put("1", 1);

        String chosen = CameraHandler.chooseCameraId(ids, map, "1");
        assertEquals("1", chosen);
    }

    @Test
    public void testChooseCameraIdExternalOverrideWhenNoExternal() {
        String[] ids = new String[]{"0", "1"};
        HashMap<String, Integer> map = new HashMap<>();
        map.put("0", 0);
        map.put("1", 1);

        String chosen = CameraHandler.chooseCameraId(ids, map, "external");
        assertEquals("0", chosen);
    }
}
