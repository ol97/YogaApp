YogaApp

In this Readme only most important files will be described. 
If a file is not described here it means that:

    a) it is fully autogenerated,
    b) it is not used,
    c) file is autogenerated and changes made to that file are minimal 
    (for example including a library in gradle file, setting home screen in manifest etc.)

YogaApp/app/src/main/java/com/example/yogaapp/ - Kotlin classes

    
    App consists of 4 Activities and 5 Fragments. 2 Activities are used only to host Fragments, so
    in reality they are hidden from the user. Activities and Fragment are responsible for large part
    of application's logic (MVC model and similar are not used).
     
    SelectionScreenActivity - Activity in which Main menu is displayed.
    
    AboutActivity - Activity in which About screen is displayed.
    
    AnalyzerActivity - Activity used only for hosting Fragments for ChallengeMode, RecordingMode 
                       and Settings, navigation between them is handled by NavHostFragment widget
                       and navigation graphs (/res/navigation/analyzer_nav_challenge.xml,
                       /res/navigation/analyzer_nav_recorder.xml). Ideally there should be only one 
                       graph with two entry points, but I had some problems with making it work with
                       configuration changes (two instances of Fragment were created). 
                       As a solution I split it into two graphs (ChallengeMode + Settings, 
                       RecordingMode + Settings). Correct one is selected in onCreate method of
                       AnalyzerActivity.
                       
                       ChallengeModeFragment - Fragment handling all logic and UI of ChallengeMode.
                       RecordingModeFragment - FragmentHandling all logic and UI of RecordingMode.
                       SettingsFragment - Fragment responsible for displaying Settings screen,
                                           everything regarding this screen is actually 
                                           configured in it's layout file
                                           (/res/xml/root_preferences.xml)   
                                                                  
    HistoryActivity - Activity used only for hosting Fragments for list view and detailed view
                      of saved training sessions. Navigation between them is handled by 
                      NavHostFragment widget and navigation graph (/res/navigation/history_nav.xml).
                      
                      ItemListFragment - Fragment in which list of all saved training sessions
                                         is displayed, partly autogenerated (using predefined
                                         List Fragment). 
                      SessionDetailsFragment - Fragment displayed after selecting an element 
                                               of the list.
                                               
                           
    Other classes: 
    
    Database
        Archive - Class responsible only for creating and updating database. Methods for all basic 
                    I/O operations are implemeted in ArchiveHelper. 
                    
        ArchiveDbSchema - Helper class in which names of tables and their columns are stored.
        
        ArchiveHelper - Helper class in which methods for all common I/O operations on database 
                        are implemented. Singleton.

    MyItemRecyclerViewAdapter - Adapter for RecyclerView used in ItemListFragment, binds slots of 
                                the list with actual elements and data from database. 
                                Partly autogenerated (using predefined List Fragment).
                                
    PoseEstimator - Class handling image processing, pose estimation, and yoga pose classification.
    
    PoseEstimatorUser - Hnterface introduced to make sending data from PoseEstimator to  
                        it's parent object a bit easier.
                        
    TimestampedPose - Class defining new datatype: TimestampedPose, it is basically 
                      Pair<String, Long>, but implements Parcelable to make saving a list of these 
                      objects in savedInstanceState possible.
                      
    YuvToRgbConverter - https://github.com/android/camera-samples/blob/master/CameraUtils/lib/src/
                        main/java/com/example/android/camera/utils/YuvToRgbConverter.kt
                        + added one method for rotating the image, used to convert images from YUV 
                        to RGB with GPU acceleration (RenderScript).
 
YogaApp/app/src/main/res/ - Resources    

    Drawable - images displayed on AboutScreen, icons, backgrounds etc.
                   
    Layouts
    
        activity_about - Layout of About screen.
        
        activity_analyzer - Layout of AnalyzerActivity, since AnalyzerActivity only hosts Fragments 
                            it's only element is NavHostFragment.
                            
        activity_history - Layout of HistoryActivity, same as activity_analyzer.
        
        activity_selection_screen - Main Menu layout.
        
        fragment_challenge_mode - ChallengeMode layouts (portrait and landscape).
        
        fragment_item_list - Layout of one list element (ItemListfragment), partly autogenerated.
        
        fragment_item_list_list - Layout of ItemListFragment, autogenerated.
        
        fragment_recorder - RecordingMode layouts (portrait and landscape).
        
        fragment_session_details - Layout of SessionDetailsFragment.
        
        res/xml/root_preferences.xml - Layout and configuration of Settings screen, 
                                       partly autogenerated
        
    Navigation - navigation graphs
                  
        analyzer_nav_challenge - Navigation Challenge Mode <--> Settings.
        
        analyzer_nav_recorder - Navigation Recording mode <--> Settings.
        
        history_nav - Navigation ItemListFragment <--> SessionDetailsFragment.                  
    
    Values 
    
        arrays - Only two arrays are defined, they are used for selecting pose estimation model
                 in selection screen.
        
        strings -Aall strings used in application and displayed to user.
        
    XML
        
        root_preferences - layout and configurations of Settings screen.
        
    ML - Machine learning models used in application (.tflite files, EfficientPose + classifier).
         Wrapper classes are generated during project build.
    
