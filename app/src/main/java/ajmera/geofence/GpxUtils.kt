package ajmera.geofence

import com.google.android.gms.maps.model.LatLng
import org.xmlpull.v1.XmlPullParser
import java.util.*

class GpxUtils {

    companion object{
        const val ATTRIBUTE_LAT="lat"
        const val ATTRIBUTE_LONG="lon"

        const val TAG_WAYPOINT="wpt"
        const val TAG_TITLE="title"
    }

    fun readGpxFile(parser: XmlPullParser):List<GpxLoc>{
        val list = mutableListOf<GpxLoc>()
        while (parser.next()!=XmlPullParser.END_TAG){
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // Starts by looking for the entry tag.
            if (parser.name == TAG_WAYPOINT) {
                list.add(readEntry(parser))
            } else {
                skip(parser)
            }
        }
        return list
    }

    private fun readEntry(parser: XmlPullParser): GpxLoc {
        parser.require(XmlPullParser.START_TAG, null, TAG_WAYPOINT)
        val lt: Double = parser.getAttributeValue(null, ATTRIBUTE_LAT).toDouble()
        val lg: Double = parser.getAttributeValue(null, ATTRIBUTE_LONG).toDouble()
        var title: String = ""
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            when (parser.name) {
                TAG_TITLE -> {
                    title = readTitle(parser)
                }
                else -> {
                    skip(parser)
                }
            }
        }
        return GpxLoc(LatLng(lt,lg), title, title)
    }

    private fun readTitle(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    private fun skip(parser: XmlPullParser) {
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}