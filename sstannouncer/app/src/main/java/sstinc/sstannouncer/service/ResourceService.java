package sstinc.sstannouncer.service;

import android.util.Log;

import java.util.Date;

import sstinc.sstannouncer.event.EventHandler;
import sstinc.sstannouncer.resource.Resource;
import sstinc.sstannouncer.resource.ResourceAcquirer;
import sstinc.sstannouncer.event.Event;
import sstinc.sstannouncer.event.EventController;

/**
 * Defines a service which maintains a resource
 *
 * @see Resource
 * @see Service
 */

public class ResourceService extends Service
{
    private Event resourceChangedEvent;
    private Event frequencyChangeEvent;

    private double frequency;

    private ResourceAcquirer acquirer;
    private Resource resource;
    private int status;

    private String serviceThreadName;
    private volatile boolean serviceThreadStop;
    private volatile boolean serviceThreadPause;
    private volatile Thread serviceThread;

    private EventController boundEventControl;


    /**
     * Constructor for ResourceService
     * Creates a new resource service that would maintain the resource passed by the caller.
     * The resource maintains the resource using the methodology provided by the ResourceAcquirer.
     * The <code>frequency</code> defines the frequency of the service to check for new updates to
     * resource.
     *
     * @param resource The resource to maintain.
     * @param acquirer The way the resource is acquired.
     *
     * @see ResourceAcquirer
     */
    public ResourceService(Resource resource, ResourceAcquirer acquirer)
    {
        this.resource = resource;
        this.acquirer = acquirer;
        this.resourceChangedEvent = new Event(String.format("service.resource.changed ",
                this.resource.getURL()), new Date(0), "");
        this.frequencyChangeEvent = new Event("service.resource.set.frequency", new Date(0), "");

        this.status = 0;
        this.serviceThreadName = "ResourceService/" + resource.getURL();
        this.serviceThreadStop = false;
        this.serviceThreadPause = false;
        this.frequency = 1.0;
    }


    /**
     * Status of the resource.
     * This value may be useful to determine the validity of the resource obtained.
     * Returns the status of the Resource Acquirer for the most recent retrival of the resource.
     * The status value is defined as per implementation of Resource Acquirer.
     *
     * @return Returns the status of the ResourceAcquirer.
     */
    public int getStatus()
    {
        return status;
    }

    /**
     * Determines if service is currently maintaining the resource.
     *
     * @return Returns if the service is currently maintaining the resource, false if not.
     */
    public boolean isAlive()
    {
        if(this.serviceThread == null) return false;
        Thread.State serviceThreadState = serviceThread.getState();
        if(serviceThreadState == Thread.State.RUNNABLE) return true;
        else return false;
    }

    /**
     * Change the Frequency of the Resource Service.
     * Change the frequency that the Resource Service checks for changes to the resource.
     * Changes to the frequency would only take effect on the next run of the service.
     * The frequency best accuracy be 1/1000 Hz.
     *
     * @param frequency Frequency to check for changes to the resource in Hertz (Hz)
     *
     */
    public void setFrequency(double frequency)
    {
        this.frequency = frequency;
    }

    /**
     * Bind to an Event Controller
     * Bind to the specified Event Controller.
     * The Resource Service would raise a <code>ResourceChanged</code> event when the resource the
     * service maintains changes.
     *
     * @param eventController-The event controller to bind to.
     */
    public void bind(EventController eventController)
    {
        final ResourceService resourceService = this;
        this.boundEventControl = eventController;
        this.boundEventControl.listen(this.toString(),
                this.getFrequencyChangeEvent().getIdentifier(),
                new EventHandler() {
                    @Override
                    public void handle(Event event) {
                        Log.d("Resource Service", event.getData());
                        double changeFrequency = Double.parseDouble(event.getData());
                        if(changeFrequency == -1.0)
                        {
                            resourceService.serviceThreadPause = true;
                        }
                        else
                        {
                            resourceService.setFrequency(changeFrequency);
                        }
                    }
                });
    }

    /**
     * Unbind from an Event Controller
     * Unbind from the specified Event Controller
     * The Resource Service would stop raising a <code>ResourceChanged</code> event when the
     * resource to service maintains changes.
     */
    public void unbind()
    {
        this.boundEventControl = null;
    }

    /**
     * Get Resource Changed Event
     * The Resource Service would raise this event when the resource is changed.
     * The event data field would the changed resource that is encoded in a string format defined by
     * <code>Resource.toString()</code>.
     * By default the event identifier is defined as "service.resource.changed", where (URL)
     * is the resource URL.
     *
     * @see Resource#toString()
     */
    public Event getResourceChangedEvent()
    {
        return this.resourceChangedEvent;
    }

    /**
     * Set Resource Changed Event
     * Change the event raised by the Resource Service when the resource changes.
     * The event would be raise with the data field overwritten with the changed resource encoded
     * is a string format defined by <code>Resource.toString()</code> and the time stamp overwritten
     * with the time that the resource was updated.
     *
     * @see Resource#toString()
     *
     * @param event The event to change the current Resource Changed Event.
     *
     */
    public void setResourceChangedEvent(Event event)
    {
        this.resourceChangedEvent = event;
    }

    /**
     * Get Frequency Change Event
     * The resource service listens for this event, on event raise, the resource service would
     * set the resource service frequency defined by the string representation of the frequency
     * in the event's data field. The string representation is defined by Double.toString().
     *
     * @return The event raise to change the resource service resource.
     */
    public Event getFrequencyChangeEvent()
    {
        return this.frequencyChangeEvent;
    }

    public void start()
    {
        if(this.isAlive() == true )  this.kill();

        this.serviceThreadStop = false;
        this.serviceThread = new Thread(this, this.serviceThreadName);
        this.serviceThread.start();
    }

    public void stop()
    {
        if(this.isAlive() == true)
        {
            this.serviceThreadStop = true;
        }
    }

    public void kill()
    {
        if(this.isAlive() == true)
        {
            this.serviceThread.interrupt();
        }
    }

    //Service Thread
    public void run()
    {
        while(this.serviceThreadStop == false)
        {
            while(this.serviceThreadPause == true){} //Pause

            Date previousTimeStamp  = (this.resource.getTimeStamp() == null) ? new Date(0) :
                    this.resource.getTimeStamp();

            this.status = this.acquirer.retrieve(this.resource);

            if(this.status == 0 && previousTimeStamp.compareTo(this.resource.getTimeStamp()) < 0)
            {
                //Retrieve Successful and Resource Changed
                if(this.boundEventControl != null)
                {
                    Event changeEvent = new Event(resourceChangedEvent.getIdentifier(),
                            this.resource.getTimeStamp(), this.resource.toString());
                    this.boundEventControl.raise(changeEvent);
                }
            }

            //Frequency Control
            double delay = 1.0/this.frequency;
            double delayMillis = (double) delay * 1000;
            try {
                Thread.sleep((long)delayMillis);
            }
            catch(InterruptedException e){
                this.kill();
            }

        }
    }
}
