package ajmera.geofence

import android.app.Application

class GeoFenceApplication : Application() {

    private var list:MutableList<GpxLoc>  = mutableListOf()

    public fun setGpxLocList(list:List<GpxLoc>){
        this.list.addAll(list)
    }

    public fun getGpxLocList():List<GpxLoc>{
        return list
    }
}