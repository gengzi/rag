import * as React from "react";
import { ChevronLeft, ChevronRight, ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { cn } from "@/lib/utils";

interface PaginationProps {
  totalItems: number;
  itemsPerPage: number;
  currentPage: number;
  onPageChange: (page: number) => void;
  onItemsPerPageChange?: (itemsPerPage: number) => void;
  showItemsPerPage?: boolean;
  pageRangeDisplayed?: number;
  className?: string;
}

const Pagination: React.FC<PaginationProps> = ({
  totalItems,
  itemsPerPage,
  currentPage,
  onPageChange,
  onItemsPerPageChange,
  showItemsPerPage = false,
  pageRangeDisplayed = 3,
  className,
}) => {
  const totalPages = Math.ceil(totalItems / itemsPerPage);
  const startPage = Math.max(1, currentPage - Math.floor(pageRangeDisplayed / 2));
  const endPage = Math.min(totalPages, startPage + pageRangeDisplayed - 1);

  const handlePrevious = () => {
    if (currentPage > 1) {
      onPageChange(currentPage - 1);
    }
  };

  const handleNext = () => {
    if (currentPage < totalPages) {
      onPageChange(currentPage + 1);
    }
  };

  const handlePageClick = (page: number) => {
    onPageChange(page);
  };

  const handleFirstPage = () => {
    onPageChange(1);
  };

  const handleLastPage = () => {
    onPageChange(totalPages);
  };

  const getPaginationItems = () => {
    const items = [];

    // Add first page button if not in range
    if (startPage > 1) {
      items.push(
        <Button
          key="first"
          variant="ghost"
          size="icon"
          onClick={handleFirstPage}
          className="h-8 w-8"
          disabled={currentPage === 1}
        >
          <span className="sr-only">First page</span>
          <ChevronLeft className="h-4 w-4" />
          <ChevronLeft className="h-4 w-4 -ml-1" />
        </Button>
      );
    }

    // Add previous page button
    items.push(
      <Button
        key="prev"
        variant="ghost"
        size="icon"
        onClick={handlePrevious}
        className="h-8 w-8"
        disabled={currentPage === 1}
      >
        <span className="sr-only">Previous page</span>
        <ChevronLeft className="h-4 w-4" />
      </Button>
    );

    // Add page number buttons
    for (let i = startPage; i <= endPage; i++) {
      items.push(
        <Button
          key={i}
          variant={currentPage === i ? "default" : "ghost"}
          size="icon"
          onClick={() => handlePageClick(i)}
          className="h-8 w-8"
        >
          <span className="sr-only">Page {i}</span>
          {i}
        </Button>
      );
    }

    // Add next page button
    items.push(
      <Button
        key="next"
        variant="ghost"
        size="icon"
        onClick={handleNext}
        className="h-8 w-8"
        disabled={currentPage === totalPages}
      >
        <span className="sr-only">Next page</span>
        <ChevronRight className="h-4 w-4" />
      </Button>
    );

    // Add last page button if not in range
    if (endPage < totalPages) {
      items.push(
        <Button
          key="last"
          variant="ghost"
          size="icon"
          onClick={handleLastPage}
          className="h-8 w-8"
          disabled={currentPage === totalPages}
        >
          <span className="sr-only">Last page</span>
          <ChevronRight className="h-4 w-4" />
          <ChevronRight className="h-4 w-4 -ml-1" />
        </Button>
      );
    }

    return items;
  };

  const getItemRangeText = () => {
    const start = (currentPage - 1) * itemsPerPage + 1;
    const end = Math.min(currentPage * itemsPerPage, totalItems);
    return `Showing ${start} to ${end} of ${totalItems} results`;
  };

  return (
    <div className={cn("flex flex-col sm:flex-row items-center justify-between gap-4 py-4", className)}>
      {showItemsPerPage && (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <span>Items per page:</span>
          <Select
            value={itemsPerPage.toString()}
            onValueChange={(value) => onItemsPerPageChange?.(parseInt(value))}
          >
            <SelectTrigger className="w-[100px]">
              <SelectValue placeholder="Items per page" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="5">5</SelectItem>
              <SelectItem value="10">10</SelectItem>
              <SelectItem value="20">20</SelectItem>
              <SelectItem value="50">50</SelectItem>
            </SelectContent>
          </Select>
        </div>
      )}
      
      <div className="flex flex-col sm:flex-row items-center gap-4">
        {showItemsPerPage && (
          <span className="text-sm text-muted-foreground">
            {getItemRangeText()}
          </span>
        )}
        
        <div className="flex items-center gap-1">
          {getPaginationItems()}
        </div>
      </div>
    </div>
  );
};

export { Pagination };