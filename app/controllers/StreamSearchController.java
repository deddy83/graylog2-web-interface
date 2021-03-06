package controllers;

import com.google.inject.Inject;
import lib.APIException;
import lib.ApiClient;
import lib.SearchTools;
import lib.timeranges.InvalidRangeParametersException;
import models.*;
import models.api.results.DateHistogramResult;
import models.api.results.SearchResult;
import play.mvc.Result;

import java.io.IOException;
import java.util.Set;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
public class StreamSearchController extends SearchController {
    @Inject
    private StreamService streamService;

    public Result index(String streamId,
                        String q,
                        String rangeType, int relative,
                        String from, String to,
                        String keyword, String interval,
                        int page,
                        String savedSearchId,
                        String sortField, String sortOrder,
                        String fields) {
        SearchSort sort = buildSearchSort(sortField, sortOrder);

        Stream stream;
        try {
            stream = streamService.get(streamId);
        } catch (IOException e) {
            return status(504, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        } catch (APIException e) {
            if (e.getHttpCode() == NOT_FOUND || e.getHttpCode() == FORBIDDEN) {
                String msg = "The requested stream was deleted and no longer exists.";
                final Startpage startpage = currentUser().getStartpage();
                if (startpage != null) {
                    if (new Startpage(Startpage.Type.STREAM, streamId).equals(startpage)) {
                        msg += " Please reset your startpage.";
                    }
                }
                flash("error", msg);
                return redirect(routes.StreamsController.index());
            }

            String message = "Unable to fetch stream. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(504, views.html.errors.error.render(message, e, request()));
        }

        String filter = "streams:" + streamId;

        UniversalSearch search;
        try {
            search = getSearch(q, filter, rangeType, relative, from, to, keyword, page, sort);
        } catch(InvalidRangeParametersException e2) {
            return status(400, views.html.errors.error.render("Invalid range parameters provided.", e2, request()));
        } catch(IllegalArgumentException e1) {
            return status(400, views.html.errors.error.render("Invalid range type provided.", e1, request()));
        }

        SearchResult searchResult;
        DateHistogramResult histogramResult;
        SavedSearch savedSearch;
        Set<String> selectedFields = getSelectedFields(fields);
        try {
            if(savedSearchId != null && !savedSearchId.isEmpty()) {
                savedSearch = savedSearchService.get(savedSearchId);
            } else {
                savedSearch = null;
            }

            // Histogram interval.
            if (interval == null || interval.isEmpty() || !SearchTools.isAllowedDateHistogramInterval(interval)) {
                interval = "minute";
            }

            searchResult = search.search();
            if (searchResult.getError() != null) {
                return ok(views.html.search.queryerror.render(currentUser(), q, searchResult, savedSearch, fields, stream));
            }
            searchResult.setAllFields(getAllFields());

            histogramResult = search.dateHistogram(interval);
        } catch (IOException e) {
            return status(504, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        } catch (APIException e) {
            String message = "There was a problem with your search. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(504, views.html.errors.error.render(message, e, request()));
        }

        if (searchResult.getTotalResultCount() > 0) {
            return ok(views.html.search.results.render(currentUser(), search, searchResult, histogramResult, q, page, savedSearch, selectedFields, stream));
        } else {
            return ok(views.html.search.noresults.render(currentUser(), q, searchResult, savedSearch, selectedFields, stream));
        }
    }

    @Override
    public Result exportAsCsv(String q, String streamId, String rangeType, int relative, String from, String to, String keyword, String fields) {
        return super.exportAsCsv(q, "streams:"+streamId, rangeType, relative, from, to, keyword, fields);
    }
}
